import { useState } from 'react'
import {
  useAlertConfigurations, useUpdateAlertConfiguration,
} from '../api/alerts'
import type { AlertConfigurationView, NotificationChannel } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Modal } from '../components/Modal'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { channelLabel, titleCase } from '../components/format'
import styles from './List.module.css'
import alertStyles from './Alerts.module.css'

const CHANNELS: NotificationChannel[] = ['IN_APP', 'EMAIL', 'SMS']

export function AlertSettingsPage() {
  const toast = useToast()
  const { data: configurations, isPending, error, refetch } = useAlertConfigurations()
  const update = useUpdateAlertConfiguration()
  const [editing, setEditing] = useState<AlertConfigurationView | null>(null)

  const save = async (input: Parameters<typeof update.mutateAsync>[0]) => {
    try {
      await update.mutateAsync(input)
      toast.success('Threshold updated')
      setEditing(null)
    } catch (err) {
      toast.fromError(err)
    }
  }

  return (
    <>
      <PageHeader
        backTo={{ to: '/alerts', label: 'Alerts' }}
        title="Alert thresholds"
        subtitle="How much warning each condition gives, and down which channels"
      />

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          {isPending ? (
            <Skeleton rows={8} />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Alert</th>
                    <th>Severity</th>
                    <th className="right">Fires at</th>
                    <th className="right">Escalates after</th>
                    <th>Channels</th>
                    <th className="right">Max snooze</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {(configurations ?? []).map((configuration) => (
                    <tr key={configuration.alertType}
                      className={`${alertStyles.configRow} ${
                        configuration.enabled ? '' : alertStyles.disabled}`}>
                      <td>
                        <div className={alertStyles.configTrack}>
                          <span className="mono">{configuration.alertCode}</span>{' '}
                          {titleCase(configuration.track)}
                        </div>
                        <div className={alertStyles.configTitle}>{configuration.title}</div>
                      </td>
                      <td><StatusPill status={configuration.category} size="sm" /></td>
                      <td className="right numeric">
                        {configuration.thresholdDays === null
                          ? <span className={`faint ${alertStyles.eventDriven}`}>on the event</span>
                          : `ETD − ${configuration.thresholdDays}d`}
                      </td>
                      <td className="right numeric">{configuration.escalationHours}h</td>
                      <td className="muted" style={{ fontSize: 12 }}>
                        {configuration.channels.map(channelLabel).join(', ')}
                      </td>
                      <td className="right numeric">{configuration.snoozeMaxHours}h</td>
                      <td className="right">
                        <button className="btn btn-sm" onClick={() => setEditing(configuration)}>
                          Edit
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className={styles.toolbar} style={{ borderTop: '1px solid var(--border)' }}>
            <span className="faint" style={{ fontSize: 11.5 }}>
              The condition itself, and who receives the alert, are not configurable — they
              are the firm's operating rules, and changing one is a change to the system.
              What varies between operations is how much warning they want.
            </span>
          </div>
        </div>
      )}

      {editing && (
        <ConfigurationModal
          configuration={editing}
          busy={update.isPending}
          onClose={() => setEditing(null)}
          onSubmit={save}
        />
      )}
    </>
  )
}

function ConfigurationModal({ configuration, busy, onClose, onSubmit }: {
  configuration: AlertConfigurationView
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    alertType: string
    enabled: boolean
    thresholdDays: number | null
    escalationHours: number
    channels: NotificationChannel[]
    snoozeMaxHours: number
    customMessage: string | null
  }) => void
}) {
  const [enabled, setEnabled] = useState(configuration.enabled)
  const [thresholdDays, setThresholdDays] = useState(configuration.thresholdDays)
  const [escalationHours, setEscalationHours] = useState(configuration.escalationHours)
  const [channels, setChannels] = useState<NotificationChannel[]>(configuration.channels)
  const [snoozeMaxHours, setSnoozeMaxHours] = useState(configuration.snoozeMaxHours)

  const toggle = (channel: NotificationChannel) =>
    setChannels((current) => current.includes(channel)
      ? current.filter((value) => value !== channel)
      : [...current, channel])

  return (
    <Modal
      title={configuration.title}
      description={`${configuration.alertCode} · goes to ${
        configuration.recipients.map((role) => titleCase(role)).join(', ')}`}
      width={560}
      onClose={onClose}
    >
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          alertType: configuration.alertType,
          enabled, thresholdDays, escalationHours, channels, snoozeMaxHours,
          customMessage: configuration.customMessage,
        })
      }}>
        <label className="checkbox">
          <input type="checkbox" checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)} />
          Watch this condition
        </label>

        <div className="form-grid">
          {configuration.thresholdDays !== null && (
            <div className="field">
              <label htmlFor="threshold">Fires at ETD minus</label>
              <input id="threshold" type="number" min={1} className="input numeric"
                value={thresholdDays ?? ''}
                onChange={(e) => setThresholdDays(Number(e.target.value))} />
              <span className="hint">days</span>
            </div>
          )}
          <div className="field">
            <label htmlFor="escalation">Escalates after</label>
            <input id="escalation" type="number" min={1} className="input numeric"
              value={escalationHours}
              onChange={(e) => setEscalationHours(Number(e.target.value))} />
            <span className="hint">hours unacknowledged</span>
          </div>
          <div className="field">
            <label htmlFor="snooze">Longest snooze</label>
            <input id="snooze" type="number" min={1} max={configuration.snoozeCeilingHours}
              className="input numeric" value={snoozeMaxHours}
              onChange={(e) => setSnoozeMaxHours(Number(e.target.value))} />
            <span className="hint">
              hours — a {configuration.category.toLowerCase()} alert is capped at{' '}
              {configuration.snoozeCeilingHours}
            </span>
          </div>
        </div>

        <div className="field">
          <label>Channels</label>
          <div className="row-wrap">
            {CHANNELS.map((channel) => (
              <label key={channel} className="checkbox">
                <input type="checkbox" checked={channels.includes(channel)}
                  onChange={() => toggle(channel)} />
                {channelLabel(channel)}
              </label>
            ))}
          </div>
          <span className="hint">
            Email and SMS are recorded but never transmitted in this system.
          </span>
        </div>

        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
