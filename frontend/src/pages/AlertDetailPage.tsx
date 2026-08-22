import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAcknowledgeAlert, useAlert, useResolveAlert, useSnoozeAlert,
} from '../api/alerts'
import type { AlertView } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { channelLabel, dateTime, relativeTime, titleCase, utcDate } from '../components/format'
import styles from './Detail.module.css'
import alertStyles from './Alerts.module.css'

type ActionKey = 'acknowledge' | 'snooze' | 'resolve'

function unavailableReason(alert: AlertView, action: ActionKey): string | null {
  if (alert.status === 'RESOLVED') {
    return 'This alert is closed'
  }
  if (action === 'acknowledge' && alert.status === 'ACKNOWLEDGED') {
    return `Already acknowledged by ${alert.acknowledgedBy}`
  }
  return null
}

export function AlertDetailPage() {
  const { id = '' } = useParams()
  const toast = useToast()
  const { data: alert, isPending, error, refetch } = useAlert(id)

  const acknowledge = useAcknowledgeAlert()
  const snooze = useSnoozeAlert()
  const resolve = useResolveAlert()
  const [dialog, setDialog] = useState<null | 'snooze' | 'resolve'>(null)

  if (isPending) return <div className="card"><Skeleton rows={7} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!alert) return null

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      toast.success(label)
      setDialog(null)
    } catch (err) {
      toast.fromError(err)
    }
  }

  const historyEntries: TimelineEntry[] = alert.history.map((entry) => ({
    key: entry.id,
    tone: entry.action === 'ESCALATED' ? 'danger'
      : entry.action === 'RESOLVED' ? 'accent' : 'default',
    title: titleCase(entry.action),
    meta: `${dateTime(entry.occurredAt)} · ${entry.actor}`,
    body: entry.notes,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/alerts', label: 'Alerts' }}
        title={alert.title}
        subtitle={
          <>
            <span className="mono">{alert.alertCode}</span> · {titleCase(alert.track)} · booking{' '}
            <Link to={`/bookings/${alert.bookingId}`} className="mono">
              {alert.bookingReference}
            </Link>
          </>
        }
        badges={
          <>
            <StatusPill status={alert.category} />
            <StatusPill status={alert.status} />
            {alert.overdue && (
              <StatusPill status="PAST_DEADLINE" tone="danger" label="Past deadline" />
            )}
          </>
        }
        actions={
          <>
            <GatedButton
              label="Acknowledge"
              reason={unavailableReason(alert, 'acknowledge')}
              variant="btn-primary"
              onClick={() => void run('Acknowledged', () => acknowledge.mutateAsync(alert.id))}
            />
            <GatedButton label="Snooze" reason={unavailableReason(alert, 'snooze')}
              onClick={() => setDialog('snooze')} />
            <GatedButton label="Resolve" reason={unavailableReason(alert, 'resolve')}
              onClick={() => setDialog('resolve')} />
          </>
        }
      />

      {alert.status === 'RESOLVED' && (
        <div className={styles.banner}>
          <strong>Closed {dateTime(alert.resolvedAt)}</strong> by {alert.resolvedBy} —{' '}
          {alert.resolutionReason}
        </div>
      )}

      {alert.status === 'ESCALATED' && (
        <div className={alertStyles.banner}>
          <strong>Escalated to {titleCase(alert.escalatedTo ?? '')}</strong>{' '}
          {relativeTime(alert.escalatedAt)}, after going unanswered.
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body stack">
            <p style={{ margin: 0, lineHeight: 1.6 }}>{alert.message}</p>
            <div className={alertStyles.action}>
              <span className={alertStyles.actionLabel}>Recommended action</span>
              {alert.recommendedAction}
            </div>
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>What happened</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                Kept in full — nothing here is edited or removed
              </span>
            </div>
            <div className="card-body">
              <Timeline entries={historyEntries} />
            </div>
          </section>

          <section className="card">
            <div className="card-header"><h2>Who and when</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Recipients"
                  value={alert.recipients.map((role) => titleCase(role)).join(', ')} />
                <Item label="Raised" value={dateTime(alert.createdAt)} />
                <Item label="Deadline"
                  value={alert.deadlineAt ? utcDate(alert.deadlineAt) : 'None set'} />
                <Item label="Last checked" value={relativeTime(alert.lastEvaluatedAt)} />
                {alert.acknowledgedAt && (
                  <Item label="Acknowledged"
                    value={`${alert.acknowledgedBy} · ${dateTime(alert.acknowledgedAt)}`} />
                )}
                {alert.snoozedUntil && (
                  <Item label="Snoozed until" value={dateTime(alert.snoozedUntil)} />
                )}
              </dl>
              <p className="faint" style={{ marginTop: 12, fontSize: 11.5 }}>
                A {alert.category.toLowerCase()} alert can be snoozed for at most{' '}
                {alert.maxSnoozeHours} hours. The deadline does not move with it.
              </p>
            </div>
          </section>
        </div>

        <section className="card">
          <div className="card-header">
            <h2>Notifications</h2>
            <span className="faint" style={{ fontSize: 12 }}>
              Recorded, not sent — nothing leaves this system
            </span>
          </div>
          <div className="card-body">
            {alert.notifications.length === 0 ? (
              <p className="muted">None recorded.</p>
            ) : (
              <div className={alertStyles.channels}>
                {alert.notifications.map((notification) => (
                  <span key={notification.id} className={alertStyles.channel}>
                    <StatusPill status={notification.deliveryStatus} size="sm" />
                    {channelLabel(notification.channel)} → {titleCase(notification.recipientRole)}
                    {notification.simulated && (
                      <span className={alertStyles.simulated}>simulated</span>
                    )}
                  </span>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>

      {dialog === 'snooze' && (
        <SnoozeModal
          maxHours={alert.maxSnoozeHours}
          category={alert.category}
          busy={snooze.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(hours) => void run(
            `Snoozed for ${hours} ${hours === 1 ? 'hour' : 'hours'}`, () =>
            snooze.mutateAsync({
              id: alert.id,
              until: new Date(Date.now() + hours * 3_600_000).toISOString(),
            }))}
        />
      )}

      {dialog === 'resolve' && (
        <ResolveModal
          busy={resolve.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Alert closed', () =>
            resolve.mutateAsync({ id: alert.id, reason }))}
        />
      )}
    </>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

function SnoozeModal({ maxHours, category, busy, onClose, onSubmit }: {
  maxHours: number
  category: string
  busy: boolean
  onClose: () => void
  onSubmit: (hours: number) => void
}) {
  const options = [1, 2, 4, 8, 24].filter((hours) => hours <= maxHours)
  const [hours, setHours] = useState(options[0])

  return (
    <Modal
      title="Snooze this alert"
      description={`A ${category.toLowerCase()} alert can be silenced for at most ${maxHours} hours. The condition and its deadline carry on regardless.`}
      onClose={onClose}
    >
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(hours) }}>
        <div className="field">
          <label htmlFor="snooze-hours">Silence for</label>
          <select id="snooze-hours" className="select" value={hours}
            onChange={(e) => setHours(Number(e.target.value))}>
            {options.map((option) => (
              <option key={option} value={option}>
                {option} {option === 1 ? 'hour' : 'hours'}
              </option>
            ))}
          </select>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Snoozing…' : 'Snooze'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ResolveModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  return (
    <Modal
      title="Close this alert"
      description="Most alerts close themselves when the underlying condition clears. Closing one by hand needs a reason, so a problem that was solved can be told apart from one that was dismissed."
      onClose={onClose}
    >
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(reason) }}>
        <div className="field">
          <label htmlFor="resolve-reason">Reason</label>
          <textarea id="resolve-reason" className="textarea" value={reason}
            onChange={(e) => setReason(e.target.value)} required
            placeholder="e.g. Truck booked by phone with the vendor" />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Closing…' : 'Close alert'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
