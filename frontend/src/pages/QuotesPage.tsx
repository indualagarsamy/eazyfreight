import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuotes } from '../api/quotes'
import type { QuoteStatus } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { date, daysUntil, money, titleCase } from '../components/format'
import styles from './List.module.css'

const FILTERS: Array<{ key: QuoteStatus | 'ALL'; label: string }> = [
  { key: 'ALL', label: 'All' },
  { key: 'DRAFT', label: 'Draft' },
  { key: 'SENT', label: 'Sent' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'DECLINED', label: 'Declined' },
  { key: 'EXPIRED', label: 'Expired' },
]

export function QuotesPage() {
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useQuotes()
  const [filter, setFilter] = useState<QuoteStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')

  const quotes = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (data ?? [])
      .filter((quote) => filter === 'ALL' || quote.status === filter)
      .filter((quote) =>
        term === '' ||
        quote.quoteReference.toLowerCase().includes(term) ||
        `${quote.originPortCode}${quote.destinationPortCode}`.toLowerCase().includes(term))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [data, filter, search])

  const counts = useMemo(() => {
    const map = new Map<string, number>()
    for (const quote of data ?? []) map.set(quote.status, (map.get(quote.status) ?? 0) + 1)
    return map
  }, [data])

  return (
    <>
      <PageHeader
        title="Quotes"
        subtitle="Rate requests from enquiry through to acceptance"
        actions={<Link to="/quotes/new" className="btn btn-primary">New quote</Link>}
      />

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
              placeholder="Search reference or lane…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              aria-label="Search quotes"
            />
          </div>

          {isPending ? (
            <Skeleton rows={5} />
          ) : quotes.length === 0 ? (
            <EmptyState
              title={data?.length ? 'No quotes match these filters' : 'No quotes yet'}
              detail={data?.length
                ? 'Try a different status or clear the search.'
                : 'Log a customer enquiry to start the quote flow.'}
              action={!data?.length && <Link to="/quotes/new" className="btn btn-primary">New quote</Link>}
            />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Lane</th>
                    <th>Mode</th>
                    <th>Status</th>
                    <th>Screening</th>
                    <th className="right">Sell</th>
                    <th className="right">Margin</th>
                    <th>Valid until</th>
                  </tr>
                </thead>
                <tbody>
                  {quotes.map((quote) => {
                    const remaining = daysUntil(quote.validUntil)
                    const lapsingSoon =
                      quote.status === 'SENT' && remaining !== null && remaining <= 3
                    return (
                      <tr
                        key={quote.id}
                        className="clickable"
                        onClick={() => navigate(`/quotes/${quote.id}`)}
                      >
                        <td className="mono">{quote.quoteReference}</td>
                        <td>{quote.originPortCode} → {quote.destinationPortCode}</td>
                        <td className="muted">{titleCase(quote.shippingMode)}</td>
                        <td><StatusPill status={quote.status} size="sm" /></td>
                        <td><StatusPill status={quote.screeningStatus} size="sm" /></td>
                        <td className="right numeric">{money(quote.totalSellRate, quote.currency)}</td>
                        <td className="right numeric">{money(quote.margin, quote.currency)}</td>
                        <td className={lapsingSoon ? styles.urgent : 'muted'}>
                          {date(quote.validUntil)}
                          {lapsingSoon && remaining !== null && (
                            <span className={styles.urgentNote}>
                              {remaining <= 0 ? ' · today' : ` · ${remaining}d`}
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  )
}
