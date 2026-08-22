import type { ApiErrorBody } from './types'

/**
 * A failed API call.
 *
 * The service distinguishes three failures and so does this: a 400 carries
 * per-field validation detail, a 409 is a command the aggregate refused because of
 * its current state, and anything else is unexpected. The 409 case is the
 * interesting one — it is not a bug, it is the domain saying no, and the UI shows
 * the reason verbatim.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string> | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors ?? {}
  }

  /** A business rule or illegal transition, not a malformed request. */
  get isDomainRuleViolation() {
    return this.status === 409
  }

  get isValidationFailure() {
    return this.status === 400
  }

  get isNotFound() {
    return this.status === 404
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: {
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        // Written into the booking audit trail. A real deployment would derive
        // this from the authenticated principal rather than a header.
        'X-Actor': 'ops.jane',
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the Eazy Freight service. Is it running on port 8080?')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const payload: unknown = text ? JSON.parse(text) : null

  if (!response.ok) {
    const error = payload as ApiErrorBody | null
    throw new ApiError(
      response.status,
      error?.message ?? `Request failed with status ${response.status}`,
      error?.fieldErrors,
    )
  }

  return payload as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
}
