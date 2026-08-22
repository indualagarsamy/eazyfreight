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

/**
 * Fetches a generated document and hands it to the browser to save.
 *
 * <p>Deliberately not {@link request}: that one parses JSON, and a PDF is not JSON.
 * The two failure modes still have to behave the same way, though — a refused command
 * comes back as an ApiError with the server's reason, not as a corrupt download.
 *
 * <p>The filename comes from Content-Disposition rather than being rebuilt here.
 * Reconstructing "HBL-2026-00001-rev0.pdf" on the client means two places that have to
 * agree about revision numbering, and they will stop agreeing.
 */
async function download(method: string, path: string): Promise<string> {
  let response: Response
  try {
    response = await fetch(path, { method, headers: { 'X-Actor': 'ops.jane' } })
  } catch {
    throw new ApiError(0, 'Cannot reach the Eazy Freight service. Is it running on port 8080?')
  }

  if (!response.ok) {
    // An error response is JSON even though the request asked for a PDF.
    const text = await response.text()
    let message = `Request failed with status ${response.status}`
    let fieldErrors: Record<string, string> | undefined
    try {
      const error = JSON.parse(text) as ApiErrorBody
      message = error.message ?? message
      fieldErrors = error.fieldErrors ?? undefined
    } catch {
      // Not JSON either. Keep the status-based message.
    }
    throw new ApiError(response.status, message, fieldErrors)
  }

  const blob = await response.blob()
  const fileName = fileNameFrom(response.headers.get('Content-Disposition')) ?? 'document.pdf'

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Revoked on the next tick: revoking synchronously can beat the click in Safari.
  setTimeout(() => URL.revokeObjectURL(url), 0)

  return fileName
}

/** Reads the filename out of a Content-Disposition header, quoted or not. */
function fileNameFrom(header: string | null): string | null {
  if (!header) return null
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (encoded) return decodeURIComponent(encoded[1])
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain ? plain[1] : null
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  download: (path: string, method: 'GET' | 'POST' = 'GET') => download(method, path),
}
