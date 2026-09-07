import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { loadArazzoWorkflow, resolveValue, runWorkflow } from '../src/arazzoRunner';

const ARAZZO_PATH = path.resolve(__dirname, '../../arazzo.yaml');

test.describe('loadArazzoWorkflow', () => {
  test('builds an operation index from the real arazzo.yaml and its 3 source specs', () => {
    const { doc, operationIndex } = loadArazzoWorkflow(ARAZZO_PATH);

    expect(doc.arazzo).toBe('1.1.0');
    expect(doc.workflows).toHaveLength(1);
    expect(doc.workflows[0].workflowId).toBe('quoteToOutboundDispatch');

    expect(operationIndex.get('quoteApi.createQuote')).toEqual({ method: 'post', path: '/api/quotes' });
    expect(operationIndex.get('bookingApi.createBooking')).toEqual({ method: 'post', path: '/api/bookings' });
    expect(operationIndex.get('bookingApi.submitBooking')).toEqual({ method: 'post', path: '/api/bookings/{id}/submit' });
    expect(operationIndex.get('bookingApi.recordCarrierConfirmation')).toEqual({
      method: 'post',
      path: '/api/bookings/{id}/carrier-confirmation',
    });
    expect(operationIndex.get('logisticsApi.dispatchOutbound')).toEqual({
      method: 'post',
      path: '/api/logistics/bookings/{bookingId}/outbound-dispatch',
    });
  });

  test('every step in the workflow resolves to a real operation', () => {
    const { doc, operationIndex } = loadArazzoWorkflow(ARAZZO_PATH);
    for (const step of doc.workflows[0].steps) {
      expect(operationIndex.has(step.operationId), `missing operation for step '${step.stepId}'`).toBe(true);
    }
  });
});

test.describe('resolveValue', () => {
  const ctx = {
    inputs: { customerId: 'cust-1', cargoDetails: [{ description: 'Widgets', pieces: 3 }] },
    steps: { createQuote: { outputs: { quoteId: 'quote-1', shippingMode: 'OCEAN_FCL' } } },
  };

  test('resolves a bare $inputs expression', () => {
    expect(resolveValue('$inputs.customerId', ctx)).toBe('cust-1');
  });

  test('resolves a bare $steps.<id>.outputs expression', () => {
    expect(resolveValue('$steps.createQuote.outputs.quoteId', ctx)).toBe('quote-1');
  });

  test('resolves expressions nested inside objects and arrays, leaving literals untouched', () => {
    const payload = {
      quoteId: '$steps.createQuote.outputs.quoteId',
      shippingMode: '$steps.createQuote.outputs.shippingMode',
      bookingSourceType: 'DIRECT_CARRIER',
      cargoDetails: '$inputs.cargoDetails',
    };
    expect(resolveValue(payload, ctx)).toEqual({
      quoteId: 'quote-1',
      shippingMode: 'OCEAN_FCL',
      bookingSourceType: 'DIRECT_CARRIER',
      cargoDetails: [{ description: 'Widgets', pieces: 3 }],
    });
  });

  test('throws a clear error when a step is referenced before it has run', () => {
    expect(() => resolveValue('$steps.dispatchOutboundTruck.outputs.logisticsId', ctx)).toThrow(/has not run yet/);
  });
});

/**
 * A stateful fake of the subset of Playwright's APIRequestContext the runner
 * uses, standing in for the 5 real backend endpoints. This lets the full
 * `runWorkflow` — including its JSON-pointer output extraction and
 * successCriteria checks — be exercised deterministically, without Docker
 * or a running Spring Boot instance, using the *real* arazzo.yaml as the
 * script.
 */
function fakeBackend() {
  const bookingId = randomUUID();
  let bookingStatus = 'BOOKING_REQUESTED';
  const dispatchId = randomUUID();

  function respond(status: number, body: unknown) {
    return { status: () => status, json: async () => body };
  }

  const request = {
    post: async (url: string, opts: { data?: unknown; headers?: Record<string, string> }) => {
      if (url === '/api/quotes') {
        return respond(201, {
          id: randomUUID(),
          quoteReference: 'Q-0001',
          customerId: (opts.data as any).customerId,
          shippingMode: (opts.data as any).shippingMode,
          originPortCode: (opts.data as any).originPortCode,
          destinationPortCode: (opts.data as any).destinationPortCode,
          incoterms: (opts.data as any).incoterms,
        });
      }
      if (url === '/api/bookings') {
        return respond(201, { id: bookingId, bookingReference: 'B-0001', status: bookingStatus });
      }
      if (url === `/api/bookings/${bookingId}/submit`) {
        bookingStatus = 'SUBMITTED_TO_CARRIER';
        return respond(200, { id: bookingId, status: bookingStatus });
      }
      if (url === `/api/bookings/${bookingId}/carrier-confirmation`) {
        bookingStatus = 'CONFIRMED_BY_CARRIER';
        return respond(200, { id: bookingId, status: bookingStatus });
      }
      if (url === `/api/logistics/bookings/${bookingId}/outbound-dispatch`) {
        return respond(201, {
          id: randomUUID(),
          stage: 'OUTBOUND_DISPATCHED',
          dispatches: [{ id: dispatchId, pickupAddress: (opts.data as any).pickupAddress }],
        });
      }
      throw new Error(`fakeBackend: unhandled POST ${url}`);
    },
  };

  return { request: request as unknown as APIRequestContext, bookingId, dispatchId };
}

test.describe('runWorkflow (against a fake backend, driven by the real arazzo.yaml)', () => {
  test('chains all 5 steps and produces the expected workflow outputs', async () => {
    const { doc, operationIndex } = loadArazzoWorkflow(ARAZZO_PATH);
    const { request, bookingId, dispatchId } = fakeBackend();

    const inputs = {
      customerId: randomUUID(),
      shipperId: randomUUID(),
      consigneeId: randomUUID(),
      shippingMode: 'OCEAN_FCL',
      originPortCode: 'USLAX',
      destinationPortCode: 'CNSHA',
      incoterms: 'FOB',
      currency: 'USD',
      shipperName: 'Acme Exports LLC',
      consigneeName: 'Global Imports Co',
      requestedEtd: '2026-11-01',
      pickupAddress: '123 Harbor Blvd, Los Angeles, CA',
      deliveryAddress: 'Carrier Yard, Long Beach, CA',
      carrierId: randomUUID(),
      carrierBookingRef: 'CARR-REF-1',
      vesselName: 'MSC Test',
      voyageNumber: 'V001E',
      confirmedEtd: '2026-11-02',
      confirmedEta: '2026-11-20',
      actor: 'playwright-tests',
      cargoDetails: [
        {
          description: 'Widgets',
          hsCode: '8471.30.0100',
          pieces: 10,
          weightKg: 500,
          lengthCm: 120,
          widthCm: 100,
          heightCm: 100,
          valueUsd: 15000,
        },
      ],
    };

    const result = await runWorkflow(request, doc, operationIndex, 'quoteToOutboundDispatch', inputs);

    expect(result.steps.createQuote.status).toBe(201);
    expect(result.steps.createBooking.status).toBe(201);
    expect(result.steps.submitBooking.status).toBe(200);
    expect(result.steps.recordCarrierConfirmation.status).toBe(200);
    expect(result.steps.dispatchOutboundTruck.status).toBe(201);

    expect(result.steps.createBooking.outputs.bookingId).toBe(bookingId);
    expect(result.steps.dispatchOutboundTruck.outputs.dispatchId).toBe(dispatchId);
    expect(result.steps.dispatchOutboundTruck.outputs.logisticsStage).toBe('OUTBOUND_DISPATCHED');

    expect(result.outputs).toMatchObject({
      bookingId,
      bookingReference: 'B-0001',
      bookingStatus: 'CONFIRMED_BY_CARRIER',
      logisticsStage: 'OUTBOUND_DISPATCHED',
    });
  });

  test('throws when a step returns a status the successCriteria does not allow', async () => {
    const { doc, operationIndex } = loadArazzoWorkflow(ARAZZO_PATH);
    const request = {
      post: async () => ({ status: () => 400, json: async () => ({ error: 'bad request' }) }),
    } as unknown as APIRequestContext;

    await expect(
      runWorkflow(request, doc, operationIndex, 'quoteToOutboundDispatch', { cargoDetails: [] }),
    ).rejects.toThrow(/expected status 201, got 400/);
  });
});
