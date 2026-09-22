import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests drive the real UI against the real service — no request
 * interception, no stubbed API. A quote that reaches ACCEPTED here reached it by
 * satisfying the aggregate's guards, which is the point: these tests are the only
 * ones in the repo that exercise the domain rules through the screens an operator
 * actually uses.
 *
 * The service must be up before they run (docker compose up -d, then ./gradlew
 * bootRun). `globalSetup` checks that and says so plainly rather than letting the
 * first test fail on an unreachable fetch.
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  // The flows write to a shared database and the lists are unfiltered, so parallel
  // workers would be watching each other's rows.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { outputFolder: 'e2e-report', open: 'never' }]],
  outputDir: 'e2e-results',

  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    // Kept for every run, pass or fail — the video *is* the deliverable here, a
    // recording of the whole quote-to-departure flow that can be watched back.
    video: { mode: 'on', size: { width: 1440, height: 900 } },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
    viewport: { width: 1440, height: 900 },
    // The recording is meant to be watched by a person, and at full speed the whole
    // flow is over in under two seconds. Set E2E_SLOW_MO=0 for a fast CI run.
    launchOptions: { slowMo: Number(process.env.E2E_SLOW_MO ?? 250) },
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  // Only the Vite dev server is started here. The Spring service is deliberately
  // not: it needs Postgres, takes a minute to boot, and a failure to start would
  // surface as an opaque Playwright timeout instead of the real cause.
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
