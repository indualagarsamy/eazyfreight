import { defineConfig } from '@playwright/test';

/**
 * These tests hit the real eazyfreight backend (see ../../../RUNNING.md to
 * start it) — there is no `webServer` entry here because bringing the stack
 * up requires Docker (Postgres) and a Gradle boot, which this config
 * deliberately does not attempt to orchestrate itself. The frontend dev
 * server (`cd frontend && npm run dev`) is a separate, optional third leg —
 * only the UI test needs it, and it self-skips if unreachable (see
 * quote-to-dispatch.spec.ts).
 */
export const FRONTEND_BASE_URL = process.env.EAZYFREIGHT_FRONTEND_URL ?? 'http://localhost:5173';

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.EAZYFREIGHT_BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
    },
  },
});
