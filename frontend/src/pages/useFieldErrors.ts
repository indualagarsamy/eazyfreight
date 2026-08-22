import { useState } from 'react'
import { ApiError } from '../api/client'

/**
 * Holds per-field messages returned by the service's 400 response.
 *
 * Bean-validation errors come back keyed by property path, e.g.
 * `cargoDetails[0].hsCode`, which maps directly onto the form's field names — so
 * server-side validation lands next to the input that caused it rather than in a
 * generic banner.
 */
export function useFieldErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({})

  return {
    errors,
    errorFor: (field: string) => errors[field],
    clear: () => setErrors({}),
    capture: (error: unknown) => {
      if (error instanceof ApiError && error.isValidationFailure) {
        setErrors(error.fieldErrors)
        return true
      }
      setErrors({})
      return false
    },
  }
}
