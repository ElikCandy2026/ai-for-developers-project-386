import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';

const PORT = 4173;
const FRONTEND_ROOT = dirname(fileURLToPath(import.meta.url));

// Playwright clears test-results before the web server starts, so every run gets
// an empty SQLite database. The config itself stays side-effect free: workers
// load it again after the server is already running.
const DATABASE_PATH = join(FRONTEND_ROOT, 'test-results', 'callbook.db');

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: 'java -jar backend/target/call-booking-1.0.0.jar',
    cwd: '..',
    url: `http://127.0.0.1:${PORT}/api/owner`,
    reuseExistingServer: false,
    timeout: 120_000,
    env: {
      PORT: String(PORT),
      DB_PATH: DATABASE_PATH,
      SEED_DEMO_DATA: 'true',
    },
  },
});
