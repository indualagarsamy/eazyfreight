import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useFilings } from '../api/compliance'
import type { FilingStatus } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { SimulationBanner } from '../components/SimulationBanner'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { date, money } from '../components/format'
import styles from './List.module.css'

const FILTERS: Array<{ key: FilingStatus | 'ALL'; label: string }> = [
  { key: 'ALL', label: 'All' },
  { key: 'DRAFT', label: 'Draft' },
  { key: 'SUBMITTED', label: 'With CBP' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'REJECTED', label: 'Rejected' },
  { key: 'CANCELLED', label: 'Cancelled' },
]

export function FilingsPage() {
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useFilings()
  const [filter, setFilter] = useState<FilingStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')

  const filings = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (data ?? [])
      .filter((filing) => filter === 'ALL' || filing.status === filter)
      .filter((filing) =>
        term === '' ||
        filing.filingReference.toLowerCase().includes(term) ||
        (filing.activeItnNumber ?? '').toLowerCase().includes(term))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [data, filter, search])

  const counts = useMemo(() => {
    const map = new Map<string, number>()
    for (const filing of data ?? []) map.set(filing.status, (map.get(filing.status) ?? 0) + 1)
    return map
  }, [data])

  return (
    <>
      <PageHeader
        title="Export compliance"
        subtitle="Electronic Export Information filed with CBP, and the ITN that gates the shipment"
      />

      <SimulationBanner />

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          <div className={styles.toolbar}>
            <div className={styles.filters} role="tablist" aria-label="Filter by status">
              {FILTERS.map((option) => (
                <button
                  key={option.key}
                  role="tab"
                  aria-selected={filter === option.key}
                  className={`${styles.filter} ${filter === option.key ? styles.filterActive : ''}`}
                  onClick={() => setFilter(option.key)}
                >
                  {option.label}
                  <span className={styles.count}>
                    {option.key === 'ALL' ? (data?.length ?? 0) : (counts.get(option.key) ?? 0)}
                  </span>
                </button>
              ))}
            </div>
            <input
              className={`input ${styles.search}`}
              placeholder="Search reference or ITN…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              aria-label="Search filings"
            />
          </div>

          {isPending ? (
            <Skeleton rows={5} />
          ) : filings.length === 0 ? (
            <EmptyState
              title={data?.length ? 'No filings match these filters' : 'No EEI filings yet'}
              detail={data?.length
                ? 'Try a different status or clear the search.'
                : 'Filings are opened from a confirmed booking.'}
            />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>ITN</th>
                    <th>Schedule B</th>
                    <th className="right">Value</th>
                    <th>ETD</th>
                  </tr>
                </thead>
                <tbody>
                  {filings.map((filing) => (
                    <tr
                      key={filing.id}
                      className="clickable"
                      onClick={() => navigate(`/compliance/${filing.id}`)}
                    >
                      <td className="mono">{filing.filingReference}</td>
                      <td><StatusPill status={filing.filingType} size="sm" /></td>
                      <td><StatusPill status={filing.status} size="sm" /></td>
                      <td className="mono">
                        {filing.activeItnNumber ?? <span className="faint">—</span>}
                      </td>
                      <td className="mono">
                        {filing.scheduleBNumber ?? '—'}
                        {filing.scheduleBNumber && !filing.scheduleBTranslated && (
                          <span className={styles.urgentNote} title="HS code carried across untranslated">
                            {' '}∗
                          </span>
                        )}
                      </td>
                      <td className="right numeric">{money(filing.valueUsd)}</td>
                      <td className="muted">{date(filing.estimatedEtd)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <p className="faint" style={{ marginTop: 12, fontSize: 11.5 }}>
        ∗ Schedule B number is an untranslated HS code. CBP dictates its own commodity
        codes; no translation table is wired up.
      </p>
    </>
  )
}
