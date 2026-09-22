const HEALTH_URL = process.env.E2E_HEALTH_URL ?? 'http://localhost:8080/actuator/health'

/**
 * Fails the run with the fix rather than the symptom.
 *
 * Without the service up, every test dies inside a React Query retry on a fetch that
 * the UI reports as "Cannot reach the Eazy Freight service" — true, but it buries the
 * one thing the reader needs to do.
 */
export default async function globalSetup() {
  let status: string | null = null
  try {
    const response = await fetch(HEALTH_URL, { signal: AbortSignal.timeout(5_000) })
    status = ((await response.json()) as { status?: string }).status ?? null
  } catch {
    status = null
  }

  if (status !== 'UP') {
    throw new Error(
      `The Eazy Freight service is not answering at ${HEALTH_URL}` +
      `${status ? ` (health reported ${status})` : ''}.\n\n` +
      'Start it before running the end-to-end tests:\n' +
      '  docker compose up -d\n' +
      '  export JAVA_HOME=/opt/homebrew/opt/openjdk@21\n' +
      '  ./gradlew bootRun\n',
    )
  }
}
