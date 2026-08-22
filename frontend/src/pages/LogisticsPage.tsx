import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAllLogistics, useAwaitingOutboundDispatch } from '../api/logistics'
import type { LogisticsStage } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { containerLabel, date } from '../components/format'
import styles from './List.module.css'

const FILTERS: Array<{ key: LogisticsStage | 'ALL' | 'BLOCKED'; label: string }> = [
  { key: 'ALL', label: 'All' },
  { key: 'BLOCKED', label: 'Blocked on ITN' },
  { key: 'OUTBOUND_DISPATCHED', label: 'Outbound' },
  { key: 'AT_CUSTOMER', label: 'At customer' },
  { key: 'SEALED', label: 'Sealed' },
  { key: 'INBOUND_DISPATCHED', label: 'Inbound' },
  { key: 'AT_TERMINAL', label: 'At terminal' },
  { key: 'UNDER_CBP_EXAMINATION', label: 'CBP hold' },
  { key: 'DEPARTED', label: 'Departed' },
]

export function LogisticsPage() {
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useAllLogistics()
  const { data: awaiting } = useAwaitingOutboundDispatch()
  const [filter, setFilter] = useState<LogisticsStage | 'ALL' | 'BLOCKED'>('ALL')

  const rows = useMemo(() => {
    return (data ?? [])
      .filter((row) =>
        filter === 'ALL' ? true
          : filter === 'BLOCKED' ? row.inboundBlockedReason !== null && row.activeSealNumber !== null
          : row.stage === filter)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [data, filter])

  const counts = useMemo(() => {
    const map = new Map<string, number>()
    for (const row of data ?? []) map.set(row.stage, (map.get(row.stage) ?? 0) + 1)
    return map
  }, [data])

  const countFor = (key: LogisticsStage | 'ALL' | 'BLOCKED') => {
    if (key === 'ALL') return data?.length ?? 0
    if (key === 'BLOCKED') {
      return (data ?? []).filter((r) => r.inboundBlockedReason !== null && r.activeSealNumber).length
    }
    return counts.get(key) ?? 0
  }

  return (
    <>
      <PageHeader
        title="Container & equipment"
        subtitle="Two truck movements, the seal history, and the ITN gate between them"
      />

      {(awaiting ?? []).length > 0 && (
        <section className="card" style={{ marginBottom: 18 }}>
          <div className="card-header">
            <h2>Awaiting outbound dispatch</h2>
            <span className="faint" style={{ fontSize: 12 }}>
              Confirmed, we arrange the trucking, no driver sent yet
            </span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Booking</th>
                  <th>Pickup</th>
                  <th>ETD</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(awaiting ?? []).map((row) => (
                  <tr key={row.bookingId}>
                    <td className="mono">{row.bookingReference}</td>
                    <td className="muted">{row.pickupAddress ?? '—'}</td>
                    <td>{date(row.confirmedEtd ?? row.requestedEtd)}</td>
                    <td className="right">
                      <Link to={`/logistics/${row.bookingId}`} className="btn btn-sm">
                        Start
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          <div className={styles.toolbar}>
            <div className={styles.filters} role="tablist" aria-label="Filter by stage">
              {FILTERS.map((option) => (
                <button
                  key={option.key}
                  role="tab"
                  aria-selected={filter === option.key}
                  className={`${styles.filter} ${filter === option.key ? styles.filterActive : ''}`}
                  onClick={() => setFilter(option.key)}
                >
                  {option.label}
                  <span className={styles.count}>{countFor(option.key)}</span>
                </button>
              ))}
            </div>
          </div>

          {isPending ? (
            <Skeleton rows={5} />
          ) : rows.length === 0 ? (
            <EmptyState
              title={data?.length ? 'Nothing at this stage' : 'No containers in motion'}
              detail={data?.length
                ? 'Try another stage.'
                : 'The track starts when the outbound truck is dispatched to the carrier yard.'}
            />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Container</th>
                    <th>Type</th>
                    <th>Stage</th>
                    <th>Seal</th>
                    <th>ITN</th>
                    <th>Movements</th>
                    <th>Flags</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="clickable"
                      onClick={() => navigate(`/logistics/${row.bookingId}`)}>
                      <td className="mono">
                        {row.containerNumber ?? <span className="faint">not yet assigned</span>}
                      </td>
                      <td className="muted">{containerLabel(row.containerType)}</td>
                      <td><StatusPill status={row.stage} size="sm" /></td>
                      <td className="mono">
                        {row.activeSealNumber ?? <span className="faint">—</span>}
                      </td>
                      <td>
                        {row.itnReceived
                          ? <span className="mono">{row.itnNumber}</span>
                          : <StatusPill status="BLOCKED" tone="warning" size="sm" label="Awaiting" />}
                      </td>
                      <td className="numeric muted">{row.dispatches.length} of 2</td>
                      <td>
                        <div className="row" style={{ gap: 5 }}>
                          {row.sealRecords.length > 1 && (
                            <StatusPill status="RESEALED" tone="info" size="sm"
                              label={`Resealed ×${row.sealRecords.length - 1}`} />
                          )}
                          {row.terminalAcceptance?.storageFeeApplies && (
                            <StatusPill status="STORAGE" tone="danger" size="sm" label="Storage fee" />
                          )}
                          {row.actualCargoDetails?.divergesMaterially && (
                            <StatusPill status="VARIANCE" tone="warning" size="sm" label="Cargo variance" />
                          )}
                          {row.documentationPreconditionsMet && (
                            <StatusPill status="DOCS" tone="positive" size="sm" label="Docs ready" />
                          )}
                          {row.sealRecords.length <= 1 && !row.terminalAcceptance?.storageFeeApplies
                            && !row.actualCargoDetails?.divergesMaterially
                            && !row.documentationPreconditionsMet && <span className="faint">—</span>}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  )
}
