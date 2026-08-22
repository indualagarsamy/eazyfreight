import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useBookings } from '../api/bookings'
import type { BookingStatus } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { date, daysUntil } from '../components/format'
import styles from './List.module.css'

const FILTERS: Array<{ key: BookingStatus | 'ALL' | 'ACTIVE'; label: string }> = [
  { key: 'ALL', label: 'All' },
  { key: 'ACTIVE', label: 'Active' },
  { key: 'BOOKING_REQUESTED', label: 'Requested' },
  { key: 'SUBMITTED_TO_CARRIER', label: 'Awaiting carrier' },
  { key: 'CONFIRMED_BY_CARRIER', label: 'Confirmed' },
  { key: 'CUSTOMER_CONFIRMED', label: 'Customer confirmed' },
  { key: 'VESSEL_OVERBOOKED', label: 'Overbooked' },
  { key: 'CANCELLED', label: 'Cancelled' },
]

const TERMINAL: BookingStatus[] = ['CANCELLED']

export function BookingsPage() {
  const navigate = useNavigate()
  const { data, isPending, error, refetch } = useBookings()
  const [filter, setFilter] = useState<BookingStatus | 'ALL' | 'ACTIVE'>('ALL')
  const [search, setSearch] = useState('')

  const bookings = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (data ?? [])
      .filter((booking) =>
        filter === 'ALL' ? true
          : filter === 'ACTIVE' ? !TERMINAL.includes(booking.status)
          : booking.status === filter)
      .filter((booking) =>
        term === '' ||
        booking.bookingReference.toLowerCase().includes(term) ||
        (booking.carrierBooking?.carrierBookingRef ?? '').toLowerCase().includes(term) ||
        (booking.carrierBooking?.vesselName ?? '').toLowerCase().includes(term))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [data, filter, search])

  const counts = useMemo(() => {
    const map = new Map<string, number>()
    for (const booking of data ?? []) map.set(booking.status, (map.get(booking.status) ?? 0) + 1)
    return map
  }, [data])

  const countFor = (key: BookingStatus | 'ALL' | 'ACTIVE') => {
    if (key === 'ALL') return data?.length ?? 0
    if (key === 'ACTIVE') return (data ?? []).filter((b) => !TERMINAL.includes(b.status)).length
    return counts.get(key) ?? 0
  }

  return (
    <>
      <PageHeader
        title="Bookings"
        subtitle="Carrier space from request through confirmation, with the full audit trail"
        actions={<Link to="/bookings/new" className="btn btn-primary">New booking</Link>}
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
                  <span className={styles.count}>{countFor(option.key)}</span>
                </button>
              ))}
            </div>
            <input
              className={`input ${styles.search}`}
              placeholder="Search reference or vessel…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              aria-label="Search bookings"
            />
          </div>

          {isPending ? (
            <Skeleton rows={5} />
          ) : bookings.length === 0 ? (
            <EmptyState
              title={data?.length ? 'No bookings match these filters' : 'No bookings yet'}
              detail={data?.length
                ? 'Try a different status or clear the search.'
                : 'Create a booking request to reserve carrier space.'}
              action={!data?.length && <Link to="/bookings/new" className="btn btn-primary">New booking</Link>}
            />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Lane</th>
                    <th>Status</th>
                    <th>Carrier ref</th>
                    <th>Vessel / voyage</th>
                    <th>ETD</th>
                    <th>Flags</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((booking) => {
                    const etd = booking.carrierBooking?.confirmedEtd ?? booking.requestedEtd
                    const remaining = daysUntil(etd)
                    const sailingSoon = remaining !== null && remaining <= 7 && remaining >= 0
                      && !TERMINAL.includes(booking.status)
                    return (
                      <tr
                        key={booking.id}
                        className="clickable"
                        onClick={() => navigate(`/bookings/${booking.id}`)}
                      >
                        <td className="mono">{booking.bookingReference}</td>
                        <td>{booking.originPortCode} → {booking.destinationPortCode}</td>
                        <td><StatusPill status={booking.status} size="sm" /></td>
                        <td className="mono muted">
                          {booking.carrierBooking?.carrierBookingRef ?? '—'}
                        </td>
                        <td>
                          {booking.carrierBooking?.vesselName ? (
                            <>
                              {booking.carrierBooking.vesselName}
                              <span className="faint"> · {booking.carrierBooking.voyageNumber}</span>
                            </>
                          ) : <span className="faint">—</span>}
                        </td>
                        <td className={sailingSoon ? styles.urgent : undefined}>
                          {date(etd)}
                          {!booking.carrierBooking?.confirmedEtd && (
                            <span className="faint" style={{ fontSize: 11.5 }}> requested</span>
                          )}
                        </td>
                        <td>
                          <div className="row" style={{ gap: 5 }}>
                            {booking.requiresCustomerEtdNotification
                              && booking.etdVarianceAcknowledgedAt === null && (
                              <StatusPill status="ETD" tone="warning" size="sm" label="ETD change" />
                            )}
                            {booking.reinstatements.length > 0 && (
                              <StatusPill status="ROLLED" tone="info" size="sm"
                                label={`Rolled ×${booking.reinstatements.length}`} />
                            )}
                            {!booking.requiresCustomerEtdNotification
                              && booking.reinstatements.length === 0
                              && <span className="faint">—</span>}
                          </div>
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
