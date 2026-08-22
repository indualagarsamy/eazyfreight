import { titleCase } from './format'
import styles from './Lifecycle.module.css'

export interface LifecycleStep {
  status: string
  label?: string
}

interface Props {
  steps: LifecycleStep[]
  current: string
  /** Statuses that end the flow off the happy path, e.g. Cancelled or Declined. */
  terminal?: { status: string; label?: string } | null
}

/**
 * The aggregate's happy path, with the current state marked.
 *
 * The point of showing this at all is that the lifecycle is a real thing the server
 * enforces — not a label someone types. When a booking ends up somewhere off the
 * path (cancelled, rejected, overbooked) the path is dimmed and the actual state is
 * shown at the end.
 */
export function Lifecycle({ steps, current, terminal }: Props) {
  const currentIndex = steps.findIndex((step) => step.status === current)
  const isOffPath = currentIndex === -1

  return (
    <div className={styles.wrap}>
      <ol className={`${styles.track} ${isOffPath ? styles.dimmed : ''}`}>
        {steps.map((step, index) => {
          const state = isOffPath
            ? 'pending'
            : index < currentIndex ? 'done' : index === currentIndex ? 'current' : 'pending'
          return (
            <li key={step.status} className={`${styles.step} ${styles[state]}`}>
              <span className={styles.marker} aria-hidden>
                {state === 'done' ? '✓' : index + 1}
              </span>
              <span className={styles.label}>{step.label ?? titleCase(step.status)}</span>
            </li>
          )
        })}
      </ol>

      {isOffPath && (
        <div className={styles.offPath}>
          <span className={styles.arrow} aria-hidden>→</span>
          <span className={styles.offPathLabel}>
            {terminal?.label ?? titleCase(current)}
          </span>
        </div>
      )}
    </div>
  )
}
