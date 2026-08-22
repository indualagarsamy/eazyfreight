import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import styles from './Toast.module.css'

type ToastTone = 'success' | 'error' | 'info'

interface Toast {
  id: number
  tone: ToastTone
  title: string
  detail?: string
}

interface ToastApi {
  success: (title: string, detail?: string) => void
  info: (title: string, detail?: string) => void
  /**
   * Surfaces a failed command. A 409 is the domain refusing the command, so its
   * message is shown verbatim and given a heading that says so rather than being
   * presented as a system error.
   */
  fromError: (error: unknown, fallbackTitle?: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const push = useCallback((tone: ToastTone, title: string, detail?: string) => {
    const id = Date.now() + Math.random()
    setToasts((current) => [...current, { id, tone, title, detail }])
    setTimeout(() => dismiss(id), tone === 'error' ? 9000 : 4500)
  }, [dismiss])

  const api = useMemo<ToastApi>(() => ({
    success: (title, detail) => push('success', title, detail),
    info: (title, detail) => push('info', title, detail),
    fromError: (error, fallbackTitle = 'Something went wrong') => {
      if (error instanceof ApiError) {
        if (error.isDomainRuleViolation) {
          push('error', 'Action not allowed', error.message)
          return
        }
        if (error.isValidationFailure) {
          const fields = Object.entries(error.fieldErrors)
          push('error', 'Check the form',
            fields.length > 0
              ? fields.map(([field, message]) => `${field}: ${message}`).join('\n')
              : error.message)
          return
        }
        push('error', fallbackTitle, error.message)
        return
      }
      push('error', fallbackTitle, error instanceof Error ? error.message : String(error))
    },
  }), [push])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className={styles.viewport} role="region" aria-label="Notifications">
        {toasts.map((toast) => (
          <div key={toast.id} className={`${styles.toast} ${styles[toast.tone]}`} role="status">
            <div className={styles.content}>
              <strong className={styles.title}>{toast.title}</strong>
              {toast.detail && <p className={styles.detail}>{toast.detail}</p>}
            </div>
            <button className={styles.close} onClick={() => dismiss(toast.id)} aria-label="Dismiss">
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside a ToastProvider')
  return context
}
