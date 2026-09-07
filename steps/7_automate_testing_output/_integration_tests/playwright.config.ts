import { defineConfig } from '@playwright/test';

/**
 * These tests hit the real eazyfreight backend (see ../../../RUNNING.md to
 * start it) — there is no `webServer` entry here because bringing the stack
 * up requires Docker (Postgres) and a Gradle boot, which this config
 * deliberately does not attempt to orchestrate itself.
 */
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
