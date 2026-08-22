import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  useAcknowledgeAlert, useAlertDashboard, useAlerts, useEvaluateAlerts,
} from '../api/alerts'
import type { AlertCategory, AlertTrack, AlertView } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { dateTime, relativeTime, titleCase } from '../components/format'
import styles from './List.module.css'
import alertStyles from './Alerts.module.css'

const CATEGORIES: AlertCategory[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const TRACKS: AlertTrack[] = [
  'LOGISTICS', 'COMPLIANCE', 'FINANCE', 'BOOKING', 'DOCUMENTATION',
]

/** Severity order, so the list reads worst-first regardless of when things happened. */
const RANK: Record<AlertCategory, number> = {
  CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3,
}

export function AlertsPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const { data: alerts, isPending, error, refetch } = useAlerts()
  const { data: dashboard } = useAlertDashboard()
  const evaluate = useEvaluateAlerts()
  const acknowledge = useAcknowledgeAlert()

  const [category, setCategory] = useState<AlertCategory | null>(null)
  const [track, setTrack] = useState<AlertTrack | null>(null)

  const visible = useMemo(() => {
    return (alerts ?? [])
      .filter((alert) => category === null || alert.category === category)
      .filter((alert) => track === null || alert.track === track)
      .sort((a, b) => RANK[a.category] - RANK[b.category]
        || a.createdAt.localeCompare(b.createdAt))
  }, [alerts, category, track])

  const runEvaluate = async () => {
    try {
      const result = await evaluate.mutateAsync()
      toast.success(result.changes === 0
        ? 'Nothing changed — every condition is as it was'
        : `${result.changes} alerts raised, resolved or escalated`)
    } catch (err) {
      toast.fromError(err)
    }
  }

  return (
    <>
      <PageHeader
        title="Alerts"
        subtitle="What needs attention right now, and who needs to know"
        actions={
          <button className="btn" disabled={evaluate.isPending} onClick={() => void runEvaluate()}>
            {evaluate.isPending ? 'Checking…' : 'Check conditions now'}
          </button>
        }
      />

      <div className={styles.summary}>
        {CATEGORIES.map((option) => (
          <button
            key={option}
            className={`${styles.stat} ${alertStyles.severityStat} ${alertStyles[option.toLowerCase()]} ${
              category === option ? alertStyles.severityActive : ''}`}
            aria-pressed={category === option}
            onClick={() => setCategory(category === option ? null : option)}
          >
            <div className={styles.statLabel}>{titleCase(option)}</div>
            <div className={styles.statValue}>{dashboard?.byCategory[option] ?? 0}</div>
          </button>
        ))}
        <div className={styles.stat}>
          <div className={styles.statLabel}>Unanswered</div>
          <div className={styles.statValue}>{dashboard?.unacknowledged ?? 0}</div>
          {(dashboard?.overdue ?? 0) > 0 && (
            <div className={styles.statNote}>{dashboard?.overdue} past deadline</div>
          )}
        </div>
      </div>

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          <div className={styles.toolbar}>
            <div className={styles.filters} role="tablist" aria-label="Filter by track">
              <button
                role="tab"
                aria-selected={track === null}
                className={`${styles.filter} ${track === null ? styles.filterActive : ''}`}
                onClick={() => setTrack(null)}
              >
                All tracks
                <span className={styles.count}>{alerts?.length ?? 0}</span>
              </button>
              {TRACKS.map((option) => (
                <button
                  key={option}
                  role="tab"
                  aria-selected={track === option}
                  className={`${styles.filter} ${track === option ? styles.filterActive : ''}`}
                  onClick={() => setTrack(track === option ? null : option)}
                >
                  {titleCase(option)}
                  <span className={styles.count}>{dashboard?.byTrack[option] ?? 0}</span>
                </button>
              ))}
            </div>
            <span className="spacer" />
            <Link to="/alerts/settings" className="btn btn-sm">Thresholds</Link>
          </div>

          {isPending ? (
            <Skeleton rows={6} />
          ) : visible.length === 0 ? (
            <EmptyState
              title={alerts?.length === 0 ? 'Nothing needs attention' : 'Nothing in this view'}
              detail={alerts?.length === 0
                ? 'Every condition the system watches is currently satisfied. The check runs again every thirty minutes.'
                : 'Clear the filters to see the rest.'}
            />
          ) : (
            <ul className={alertStyles.list}>
              {visible.map((alert) => (
                <AlertRow
                  key={alert.id}
                  alert={alert}
                  onOpen={() => navigate(`/alerts/${alert.id}`)}
                  onAcknowledge={async () => {
                    try {
                      await acknowledge.mutateAsync(alert.id)
                      toast.success('Acknowledged')
                    } catch (err) {
                      toast.fromError(err)
                    }
                  }}
                />
              ))}
            </ul>
          )}
        </div>
      )}
    </>
  )
}

function AlertRow({ alert, onOpen, onAcknowledge }: {
  alert: AlertView
  onOpen: () => void
  onAcknowledge: () => void
}) {
  return (
    <li className={`${alertStyles.row} ${alertStyles[alert.category.toLowerCase()]}`}>
      <div className={alertStyles.rowMain}>
        <div className={alertStyles.rowHead}>
          <span className={`mono ${alertStyles.code}`}>{alert.alertCode}</span>
          <button className={alertStyles.rowTitle} onClick={onOpen}>{alert.title}</button>
          <StatusPill status={alert.category} size="sm" />
          {alert.status !== 'ACTIVE' && <StatusPill status={alert.status} size="sm" />}
          {alert.overdue && (
            <StatusPill status="PAST_DEADLINE" tone="danger" size="sm" label="Past deadline" />
          )}
        </div>
        <p className={alertStyles.message}>{alert.message}</p>
        <div className={alertStyles.rowMeta}>
          <Link to={`/bookings/${alert.bookingId}`} className="mono">
            {alert.bookingReference}
          </Link>
          <span aria-hidden>·</span>
          <span>{alert.recipients.map((role) => titleCase(role)).join(', ')}</span>
          <span aria-hidden>·</span>
          <span title={dateTime(alert.createdAt)}>raised {relativeTime(alert.createdAt)}</span>
          {alert.deadlineAt && (
            <>
              <span aria-hidden>·</span>
              <span className={alert.overdue ? alertStyles.late : undefined}>
                due {relativeTime(alert.deadlineAt)}
              </span>
            </>
          )}
        </div>
      </div>
      <div className={alertStyles.rowActions}>
        {alert.status === 'ACTIVE' || alert.status === 'ESCALATED' ? (
          <button className="btn btn-sm" onClick={onAcknowledge}>Acknowledge</button>
        ) : alert.status === 'SNOOZED' ? (
          <span className="faint">until {dateTime(alert.snoozedUntil)}</span>
        ) : (
          <span className="faint">{alert.acknowledgedBy}</span>
        )}
        <button className="btn btn-sm btn-ghost" onClick={onOpen}>Open</button>
      </div>
    </li>
  )
}
