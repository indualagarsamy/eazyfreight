import fs from 'node:fs';
import path from 'node:path';
import yaml from 'js-yaml';
import type { APIRequestContext } from '@playwright/test';

/**
 * A minimal Arazzo 1.1.0 executor, scoped deliberately to the subset of the
 * spec that ../arazzo.yaml actually uses:
 *   - steps addressed by `operationId` only (no `operationPath`/`workflowId` steps)
 *   - `successCriteria` conditions of the form `$statusCode == <n>` only
 *   - step `outputs` of the form `$response.body#/<json-pointer>` only
 *   - `requestBody.payload` / workflow `outputs` values that are plain
 *     `$inputs.<name>` or `$steps.<id>.outputs.<name>` runtime expressions
 *     (possibly nested inside objects/arrays), not expressions embedded
 *     inside a larger string
 *
 * This is not a general-purpose Arazzo runtime — it exists to prove that
 * arazzo.yaml, read as data, actually drives a passing test against the
 * real API. If arazzo.yaml starts using a feature outside this list, this
 * file should grow to match it rather than silently ignoring the feature.
 */

export interface ArazzoDoc {
  arazzo: string;
  info: { title: string; version: string };
  sourceDescriptions: { name: string; url: string; type: string }[];
  workflows: ArazzoWorkflow[];
}

export interface ArazzoWorkflow {
  workflowId: string;
  inputs?: unknown;
  steps: ArazzoStep[];
  outputs?: Record<string, string>;
}

export interface ArazzoStep {
  stepId: string;
  operationId: string;
  parameters?: { name: string; in: 'path' | 'query' | 'header' | 'cookie'; value: unknown }[];
  requestBody?: { contentType: string; payload: unknown };
  successCriteria?: { condition: string }[];
  outputs?: Record<string, string>;
}

export interface Operation {
  method: 'get' | 'post' | 'put' | 'patch' | 'delete';
  path: string;
}

export interface WorkflowContext {
  inputs: Record<string, unknown>;
  steps: Record<string, { outputs: Record<string, unknown> }>;
}

export interface StepResult {
  stepId: string;
  method: string;
  path: string;
  status: number;
  body: unknown;
  outputs: Record<string, unknown>;
}

export interface WorkflowResult {
  steps: Record<string, StepResult>;
  outputs: Record<string, unknown>;
}

/** Loads arazzo.yaml plus every OpenAPI spec it references, and builds an operationId -> {method, path} index. */
export function loadArazzoWorkflow(arazzoFilePath: string): { doc: ArazzoDoc; operationIndex: Map<string, Operation> } {
  const doc = yaml.load(fs.readFileSync(arazzoFilePath, 'utf8')) as ArazzoDoc;
  const baseDir = path.dirname(arazzoFilePath);
  const operationIndex = new Map<string, Operation>();

  for (const source of doc.sourceDescriptions) {
    const specPath = path.resolve(baseDir, source.url);
    const spec = yaml.load(fs.readFileSync(specPath, 'utf8')) as {
      servers?: { url: string }[];
      paths: Record<string, Record<string, { operationId?: string }>>;
    };
    const serverUrl = spec.servers?.[0]?.url ?? '';

    for (const [pathKey, pathItem] of Object.entries(spec.paths)) {
      for (const method of ['get', 'post', 'put', 'patch', 'delete'] as const) {
        const operation = pathItem[method];
        if (!operation?.operationId) continue;
        const fullPath = pathKey === '/' ? serverUrl : `${serverUrl}${pathKey}`;
        operationIndex.set(`${source.name}.${operation.operationId}`, { method, path: fullPath });
      }
    }
  }

  return { doc, operationIndex };
}

/** Resolves `$inputs.x` and `$steps.<id>.outputs.x` anywhere inside a value, recursively. */
export function resolveValue(value: unknown, ctx: WorkflowContext): unknown {
  if (typeof value === 'string') {
    const inputMatch = value.match(/^\$inputs\.(.+)$/);
    if (inputMatch) return getByDottedPath(ctx.inputs, inputMatch[1]);

    const stepMatch = value.match(/^\$steps\.([^.]+)\.outputs\.(.+)$/);
    if (stepMatch) {
      const [, stepId, outputPath] = stepMatch;
      const stepOutputs = ctx.steps[stepId]?.outputs;
      if (!stepOutputs) {
        throw new Error(`arazzoRunner: step '${stepId}' has not run yet (referenced as $steps.${stepId}.outputs.${outputPath})`);
      }
      return getByDottedPath(stepOutputs, outputPath);
    }

    return value;
  }
  if (Array.isArray(value)) return value.map((item) => resolveValue(item, ctx));
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolveValue(v, ctx)]));
  }
  return value;
}

function getByDottedPath(obj: unknown, dottedPath: string): unknown {
  return dottedPath.split('.').reduce<unknown>((acc, key) => {
    if (acc == null) return acc;
    return (acc as Record<string, unknown>)[key];
  }, obj);
}

/** Resolves a `$response.body#/<pointer>` (or bare `$statusCode`) expression against one step's response. */
function resolveResponseExpression(expr: string, response: { status: number; body: unknown }): unknown {
  if (expr === '$statusCode') return response.status;
  const bodyMatch = expr.match(/^\$response\.body#\/(.*)$/);
  if (bodyMatch) return getJsonPointer(response.body, bodyMatch[1]);
  throw new Error(`arazzoRunner: unsupported output expression '${expr}'`);
}

function getJsonPointer(obj: unknown, pointer: string): unknown {
  if (pointer === '') return obj;
  return pointer.split('/').reduce<unknown>((acc, rawSegment) => {
    if (acc == null) return acc;
    const segment = rawSegment.replace(/~1/g, '/').replace(/~0/g, '~');
    return Array.isArray(acc) ? acc[Number(segment)] : (acc as Record<string, unknown>)[segment];
  }, obj);
}

/** Evaluates `successCriteria` entries of the form `$statusCode == <n>`. Throws with a descriptive message on the first failure. */
function checkSuccessCriteria(criteria: { condition: string }[] | undefined, response: { status: number }, stepId: string): void {
  for (const { condition } of criteria ?? []) {
    const match = condition.match(/^\$statusCode\s*==\s*(\d+)$/);
    if (!match) throw new Error(`arazzoRunner: unsupported successCriteria condition '${condition}' on step '${stepId}'`);
    const expected = Number(match[1]);
    if (response.status !== expected) {
      throw new Error(`arazzoRunner: step '${stepId}' expected status ${expected}, got ${response.status}`);
    }
  }
}

async function runStep(
  request: APIRequestContext,
  operationIndex: Map<string, Operation>,
  step: ArazzoStep,
  ctx: WorkflowContext,
): Promise<StepResult> {
  const operation = operationIndex.get(step.operationId);
  if (!operation) {
    throw new Error(`arazzoRunner: operationId '${step.operationId}' not found in any sourceDescription`);
  }

  let resolvedPath = operation.path;
  const headers: Record<string, string> = {};
  for (const param of step.parameters ?? []) {
    const resolved = resolveValue(param.value, ctx);
    if (param.in === 'path') {
      resolvedPath = resolvedPath.replace(`{${param.name}}`, encodeURIComponent(String(resolved)));
    } else if (param.in === 'header') {
      headers[param.name] = String(resolved);
    } else {
      throw new Error(`arazzoRunner: unsupported parameter location '${param.in}' on step '${step.stepId}'`);
    }
  }

  const payload = step.requestBody ? resolveValue(step.requestBody.payload, ctx) : undefined;
  const response = await request[operation.method](resolvedPath, {
    headers,
    ...(payload !== undefined ? { data: payload } : {}),
  });

  const status = response.status();
  const body = await response.json().catch(() => undefined);

  checkSuccessCriteria(step.successCriteria, { status }, step.stepId);

  const outputs = step.outputs
    ? Object.fromEntries(
        Object.entries(step.outputs).map(([name, expr]) => [name, resolveResponseExpression(expr, { status, body })]),
      )
    : {};

  return { stepId: step.stepId, method: operation.method, path: resolvedPath, status, body, outputs };
}

/** Runs every step of `workflowId` in order against a live backend via Playwright's `request` fixture. */
export async function runWorkflow(
  request: APIRequestContext,
  doc: ArazzoDoc,
  operationIndex: Map<string, Operation>,
  workflowId: string,
  inputs: Record<string, unknown>,
): Promise<WorkflowResult> {
  const workflow = doc.workflows.find((w) => w.workflowId === workflowId);
  if (!workflow) throw new Error(`arazzoRunner: workflow '${workflowId}' not found`);

  const ctx: WorkflowContext = { inputs, steps: {} };
  const steps: Record<string, StepResult> = {};

  for (const step of workflow.steps) {
    const result = await runStep(request, operationIndex, step, ctx);
    ctx.steps[step.stepId] = { outputs: result.outputs };
    steps[step.stepId] = result;
  }

  const outputs = workflow.outputs
    ? Object.fromEntries(Object.entries(workflow.outputs).map(([name, expr]) => [name, resolveValue(expr, ctx)]))
    : {};

  return { steps, outputs };
}
