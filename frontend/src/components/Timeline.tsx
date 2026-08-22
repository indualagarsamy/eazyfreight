import type { ReactNode } from 'react'
import styles from './Timeline.module.css'

export interface TimelineEntry {
  key: string
  title: ReactNode
  meta?: ReactNode
  body?: ReactNode
  tone?: 'default' | 'accent' | 'danger'
}

/** Chronological record — used for the booking audit trail and reinstatement history. */
export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  return (
    <ol className={styles.timeline}>
      {entries.map((entry) => (
        <li key={entry.key} className={`${styles.entry} ${styles[entry.tone ?? 'default']}`}>
          <span className={styles.node} aria-hidden />
          <div className={styles.content}>
            <div className={styles.head}>
              <span className={styles.title}>{entry.title}</span>
              {entry.meta && <span className={styles.meta}>{entry.meta}</span>}
            </div>
            {entry.body && <div className={styles.body}>{entry.body}</div>}
          </div>
        </li>
      ))}
    </ol>
  )
}
