import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useActiveHouseBols } from '../api/documentation'
import type { ReleaseType } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import styles from './List.module.css'

const FILTERS: Array<{ key: ReleaseType | 'ALL' | 'ORIGINALS_OUT'; label: string }> = [
  { key: 'ALL', label: 'All' },
  { key: 'ORIGINALS_OUT', label: 'Originals outstanding' },
  { key: 'ORIGINAL_BOL', label: 'Original BOL' },
  { key: 'TELEX_RELEASE', label: 'Telex release' },
  { key: 'SEA_WAYBILL', label: 'Sea waybill' },
]

export function DocumentationPage() {
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useActiveHouseBols()
  const [filter, setFilter] = useState<ReleaseType | 'ALL' | 'ORIGINALS_OUT'>('ALL')

  const rows = useMemo(() => (data ?? [])
    .filter((bol) =>
      filter === 'ALL' ? true
        : filter === 'ORIGINALS_OUT' ? (bol.originals !== null && !bol.originals.allSurrendered)
        : bol.releaseType === filter)
    .sort((a, b) => b.issuedAt.localeCompare(a.issuedAt)), [data, filter])

  const countFor = (key: ReleaseType | 'ALL' | 'ORIGINALS_OUT') => {
    if (key === 'ALL') return data?.length ?? 0
    if (key === 'ORIGINALS_OUT') {
      return (data ?? []).filter((b) => b.originals && !b.originals.allSurrendered).length
    }
    return (data ?? []).filter((b) => b.releaseType === key).length
  }

  return (
    <>
      <PageHeader
        title="Documentation"
        subtitle="Bills of lading — where the logistics, compliance and booking tracks converge"
      />

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          <div className={styles.toolbar}>
            <div className={styles.filters} role="tablist" aria-label="Filter by release type">
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
              title={data?.length ? 'None match this filter' : 'No bills of lading issued'}
              detail={data?.length
                ? 'Try another release type.'
                : 'A House BOL needs a container number, a seal, an ITN and a verified Master BOL. Open a booking to see what is still outstanding.'}
            />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>House BOL</th>
                    <th>Rev</th>
                    <th>Release</th>
                    <th>Container / seal</th>
                    <th>Vessel</th>
                    <th>Consignee</th>
                    <th>Flags</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((bol) => (
                    <tr key={bol.id} className="clickable"
                      onClick={() => navigate(`/documentation/${bol.id}`)}>
                      <td className="mono">{bol.houseBolNumber}</td>
                      <td className="numeric">
                        {bol.revisionNumber > 0
                          ? <StatusPill status="REV" tone="info" size="sm"
                              label={`Rev ${bol.revisionNumber}`} />
                          : <span className="faint">0</span>}
                      </td>
                      <td><StatusPill status={bol.releaseType} size="sm" /></td>
                      <td className="mono">
                        {bol.containerNumber}
                        <span className="faint"> · {bol.sealNumber}</span>
                      </td>
                      <td className="muted">
                        {bol.vesselName ?? '—'}
                        {bol.voyageNumber && <span className="faint"> · {bol.voyageNumber}</span>}
                      </td>
                      <td className="muted">{bol.consigneeNameSnapshot}</td>
                      <td>
                        <div className="row" style={{ gap: 5 }}>
                          {bol.originals && !bol.originals.allSurrendered && (
                            <StatusPill status="ORIG" tone="warning" size="sm"
                              label={`${bol.originals.outstanding} originals out`} />
                          )}
                          {bol.distributions.length === 0 && (
                            <StatusPill status="UNSENT" tone="neutral" size="sm" label="Not sent" />
                          )}
                          {bol.originals?.allSurrendered && (
                            <StatusPill status="SURR" tone="positive" size="sm" label="Surrendered" />
                          )}
                          {!bol.originals && bol.distributions.length > 0 && (
                            <span className="faint">—</span>
                          )}
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
