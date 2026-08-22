import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import styles from './States.module.css'

export function Skeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className={styles.skeleton} aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className={styles.bar} style={{ width: `${94 - index * 9}%` }} />
      ))}
    </div>
  )
}

export function EmptyState({ title, detail, action }: {
  title: string
  detail?: string
  action?: ReactNode
}) {
  return (
    <div className={styles.empty}>
      <p className={styles.emptyTitle}>{title}</p>
      {detail && <p className={styles.emptyDetail}>{detail}</p>}
      {action && <div className={styles.emptyAction}>{action}</div>}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const isOffline = error instanceof ApiError && error.status === 0
  const message = error instanceof Error ? error.message : String(error)

  return (
    <div className={styles.error}>
      <p className={styles.errorTitle}>
        {isOffline ? 'Cannot reach the service' : 'Could not load this'}
      </p>
      <p className={styles.errorDetail}>{message}</p>
      {isOffline && (
        <p className={styles.errorHint}>
          Start it with <code className="mono">./gradlew bootRun</code> from the service
          directory, with <code className="mono">docker compose up -d</code> running first.
        </p>
      )}
      {onRetry && (
        <button className="btn btn-sm" onClick={onRetry} style={{ marginTop: 12 }}>
          Try again
        </button>
      )}
    </div>
  )
}
