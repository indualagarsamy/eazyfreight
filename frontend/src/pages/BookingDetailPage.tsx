import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAcceptCounterOffer, useAcknowledgeEtdVariance, useBooking, useCancelBooking,
  useRecordCarrierConfirmation, useRecordCarrierRejection, useRecordCounterOffer,
  useRecordVesselOverbooking, useReinstateBooking,
  useRejectCounterOffer, useSendBookingConfirmation, useSubmitBooking,
} from '../api/bookings'
import type { Booking, BookingSourceType, CancellationInitiator, ContainerType } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Lifecycle } from '../components/Lifecycle'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { useFilingsForBooking, useInitiateFiling } from '../api/compliance'
import { containerLabel, date, dateTime, number, shortId, titleCase } from '../components/format'
import styles from './Detail.module.css'

const DEMO_CARRIER_ID = '1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d'

const LIFECYCLE = [
  { status: 'BOOKING_REQUESTED', label: 'Requested' },
  { status: 'SUBMITTED_TO_CARRIER', label: 'With carrier' },
  { status: 'CONFIRMED_BY_CARRIER', label: 'Confirmed' },
  { status: 'CUSTOMER_CONFIRMED', label: 'Customer confirmed' },
]

type ActionKey =
  | 'submit' | 'confirm' | 'reject' | 'counterOffer' | 'acceptCounter' | 'rejectCounter'
  | 'sendConfirmation' | 'overbook' | 'reinstate' | 'cancel'

/**
 * Mirrors the aggregate's guards so an action can be disabled with the reason
 * shown, rather than offered and then refused. The server is still the authority:
 * anything that slips through comes back as a 409 and surfaces as a toast.
 */
function unavailableReason(booking: Booking, action: ActionKey): string | null {
  const { status } = booking
  switch (action) {
    case 'submit':
      return status === 'BOOKING_REQUESTED' || status === 'REJECTED_BY_CARRIER'
        ? null : 'Only a requested or rejected booking can be submitted'
    case 'confirm':
      return status === 'SUBMITTED_TO_CARRIER' || status === 'COUNTER_OFFER_RECEIVED'
        ? null : 'The booking must be with the carrier first'
    case 'reject':
    case 'counterOffer':
      return status === 'SUBMITTED_TO_CARRIER' ? null : 'The booking is not awaiting a carrier answer'
    case 'acceptCounter':
    case 'rejectCounter':
      return status === 'COUNTER_OFFER_RECEIVED' ? null : 'No counter-offer is outstanding'
    case 'sendConfirmation':
      if (status !== 'CONFIRMED_BY_CARRIER' && status !== 'CUSTOMER_CONFIRMED') {
        return 'The carrier has not confirmed yet'
      }
      if (booking.requiresCustomerEtdNotification && booking.etdVarianceAcknowledgedAt === null) {
        return 'Acknowledge the ETD change with the customer first'
      }
      return null
    case 'overbook':
      return status === 'CONFIRMED_BY_CARRIER' || status === 'CUSTOMER_CONFIRMED'
        ? null : 'Only a confirmed booking can be reported as overbooked'
    case 'reinstate':
      if (status === 'VESSEL_OVERBOOKED') return null
      if (status === 'CANCELLED' && booking.cancellationInitiatedBy === 'CARRIER_OVERBOOKING') return null
      return 'Only a booking overbooked by the carrier can be rolled forward'
    case 'cancel':
      return status === 'CANCELLED' ? 'Already cancelled' : null
  }
}

export function BookingDetailPage() {
  const { id = '' } = useParams()
  const toast = useToast()
  const { data: booking, isPending, error, refetch } = useBooking(id)

  const submit = useSubmitBooking(id)
  const confirm = useRecordCarrierConfirmation(id)
  const reject = useRecordCarrierRejection(id)
  const counterOffer = useRecordCounterOffer(id)
  const acceptCounter = useAcceptCounterOffer(id)
  const rejectCounter = useRejectCounterOffer(id)
  const acknowledge = useAcknowledgeEtdVariance(id)
  const sendConfirmation = useSendBookingConfirmation(id)
  const overbook = useRecordVesselOverbooking(id)
  const reinstate = useReinstateBooking(id)
  const cancel = useCancelBooking(id)

  const { data: filings } = useFilingsForBooking(id)
  const initiateFiling = useInitiateFiling()

  const [dialog, setDialog] = useState<null | 'submit' | 'confirm' | 'reject' | 'counter' | 'reinstate' | 'cancel'>(null)

  if (isPending) return <div className="card"><Skeleton rows={7} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!booking) return null

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      toast.success(label)
      setDialog(null)
    } catch (err) {
      toast.fromError(err)
    }
  }

  const Action = ({ action, label, onClick, variant = '' }: {
    action: ActionKey
    label: string
    onClick: () => void
    variant?: string
  }) => (
    <GatedButton
      label={label}
      reason={unavailableReason(booking, action)}
      onClick={onClick}
      variant={variant}
      size="sm"
    />
  )

  const carrier = booking.carrierBooking
  const etdPending = booking.requiresCustomerEtdNotification
    && booking.etdVarianceAcknowledgedAt === null

  const historyEntries: TimelineEntry[] = booking.statusHistory.map((entry) => ({
    key: entry.id,
    tone: entry.toStatus === 'CANCELLED' ? 'danger'
      : entry.toStatus === 'CONFIRMED_BY_CARRIER' ? 'accent' : 'default',
    title: (
      <>
        {entry.fromStatus && (
          <span className="faint">{titleCase(entry.fromStatus)} → </span>
        )}
        {titleCase(entry.toStatus)}
      </>
    ),
    meta: `${dateTime(entry.changedAt)} · ${entry.changedBy} · ${titleCase(entry.source)}`,
    body: entry.reason,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/bookings', label: 'Bookings' }}
        title={<span className="mono">{booking.bookingReference}</span>}
        subtitle={`${booking.originPortCode} → ${booking.destinationPortCode} · ${titleCase(booking.shippingMode)} · ${booking.incoterms}`}
        badges={
          <>
            <StatusPill status={booking.status} />
            <section className="card">
          <div className="card-header">
            <h2>Export compliance</h2>
            {(filings ?? []).length === 0 && (
              <button
                className="btn btn-sm"
                disabled={initiateFiling.isPending}
                onClick={() => void run('EEI filing opened',
                  () => initiateFiling.mutateAsync(booking.id))}
              >
                Open EEI filing
              </button>
            )}
          </div>
          <div className="card-body">
            {(filings ?? []).length === 0 ? (
              <p className="muted">
                No EEI filing yet. The ITN gates both the inbound truck dispatch and the
                Master BOL instructions, so it is the first thing to raise after confirmation.
              </p>
            ) : (
              <div className="stack" style={{ gap: 10 }}>
                {(filings ?? []).map((filing) => (
                  <Link key={filing.id} to={`/compliance/${filing.id}`} className={styles.filingRow}>
                    <span className="mono">{filing.filingReference}</span>
                    <StatusPill status={filing.filingType} size="sm" />
                    <StatusPill status={filing.status} size="sm" />
                    <span className="spacer" />
                    {filing.activeItnNumber
                      ? <span className="mono">{filing.activeItnNumber}</span>
                      : <span className="faint">no ITN</span>}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>

        {booking.reinstatements.length > 0 && (
              <StatusPill status="ROLLED" tone="info"
                label={`Rolled ×${booking.reinstatements.length}`} />
            )}
            {booking.itnFiled && <StatusPill status="ITN" tone="positive" label="ITN filed" />}
          </>
        }
        actions={
          <>
            <Action action="submit" label="Submit to carrier"
              onClick={() => setDialog('submit')} variant="btn-primary" />
            <Action action="confirm" label="Record confirmation" onClick={() => setDialog('confirm')} />
            <Action action="sendConfirmation" label="Send confirmation"
              onClick={() => void run('Confirmation sent to customer', () => sendConfirmation.mutateAsync())} />
          </>
        }
      />

      {etdPending && carrier && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          <div className={styles.bannerRow}>
            <div style={{ flex: 1, minWidth: 240 }}>
              <strong>Confirmed ETD moved more than two business days.</strong>{' '}
              Requested {date(booking.requestedEtd)}, confirmed {date(carrier.confirmedEtd)}.
              The customer must be told before the booking confirmation goes out.
            </div>
            <button className="btn btn-sm"
              onClick={() => void run('ETD change acknowledged', () => acknowledge.mutateAsync())}>
              Customer notified
            </button>
          </div>
        </div>
      )}

      {booking.status === 'CANCELLED' && (
        <div className={styles.banner}>
          <strong>Cancelled {dateTime(booking.cancelledAt)}</strong> by{' '}
          {titleCase(booking.cancellationInitiatedBy ?? 'UNKNOWN')} — {booking.cancellationReason}
          {booking.cancellationInitiatedBy === 'CARRIER_OVERBOOKING' &&
            ' · This booking can still be rolled to a later sailing.'}
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <Lifecycle steps={LIFECYCLE} current={booking.status} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Carrier response</span>
            <Action action="reject" label="Carrier rejected" onClick={() => setDialog('reject')} />
            <Action action="counterOffer" label="Counter-offer" onClick={() => setDialog('counter')} />
            <Action action="acceptCounter" label="Accept counter-offer"
              onClick={() => void run('Counter-offer accepted', () => acceptCounter.mutateAsync())} />
            <Action action="rejectCounter" label="Reject counter-offer"
              onClick={() => void run('Counter-offer rejected', () => rejectCounter.mutateAsync())} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Exceptions</span>
            <Action action="overbook" label="Vessel overbooked"
              onClick={() => void run('Overbooking recorded', () => overbook.mutateAsync())} />
            <Action action="reinstate" label="Roll to next sailing" onClick={() => setDialog('reinstate')} />
            <Action action="cancel" label="Cancel booking"
              onClick={() => setDialog('cancel')} variant="btn-danger" />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header"><h2>Carrier booking</h2></div>
            <div className="card-body">
              {!carrier ? (
                <p className="muted">Not submitted to a carrier yet.</p>
              ) : (
                <dl className="definition-list">
                  <Item label="Source" value={titleCase(carrier.bookingSourceType)} />
                  <Item label="Carrier ref" value={carrier.carrierBookingRef ?? 'Awaiting'} mono />
                  {carrier.coLoaderBookingRef && (
                    <Item label="Co-loader ref" value={carrier.coLoaderBookingRef} mono />
                  )}
                  <Item label="Vessel" value={carrier.vesselName ?? '—'} />
                  <Item label="Voyage" value={carrier.voyageNumber ?? '—'} />
                  <Item label="Confirmed ETD" value={date(carrier.confirmedEtd)} />
                  <Item label="Confirmed ETA" value={date(carrier.confirmedEta)} />
                  <Item label="Container" value={containerLabel(carrier.containerType)} />
                  <Item label="Confirmed by" value={carrier.confirmedBy ?? '—'} />
                </dl>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-header"><h2>Trucking</h2></div>
            <div className="card-body">
              {!booking.transportRequired ? (
                <p className="muted">Customer arranges their own trucking.</p>
              ) : (
                <>
                  <dl className="definition-list">
                    <Item label="Pickup address" value={booking.pickupAddress ?? '—'} />
                  </dl>
                  <p className="muted" style={{ marginTop: 14, fontSize: 12.5 }}>
                    Truck movements are managed in{' '}
                    <Link to={`/logistics/${booking.id}`}>Container &amp; equipment</Link> —
                    there are two of them, and the second is gated on the ITN.
                  </p>
                </>
              )}
            </div>
          </section>
        </div>

        <section className="card">
          <div className="card-header">
            <h2>Export compliance</h2>
            {(filings ?? []).length === 0 && (
              <button
                className="btn btn-sm"
                disabled={initiateFiling.isPending}
                onClick={() => void run('EEI filing opened',
                  () => initiateFiling.mutateAsync(booking.id))}
              >
                Open EEI filing
              </button>
            )}
          </div>
          <div className="card-body">
            {(filings ?? []).length === 0 ? (
              <p className="muted">
                No EEI filing yet. The ITN gates both the inbound truck dispatch and the
                Master BOL instructions, so it is the first thing to raise after confirmation.
              </p>
            ) : (
              <div className="stack" style={{ gap: 10 }}>
                {(filings ?? []).map((filing) => (
                  <Link key={filing.id} to={`/compliance/${filing.id}`} className={styles.filingRow}>
                    <span className="mono">{filing.filingReference}</span>
                    <StatusPill status={filing.filingType} size="sm" />
                    <StatusPill status={filing.status} size="sm" />
                    <span className="spacer" />
                    {filing.activeItnNumber
                      ? <span className="mono">{filing.activeItnNumber}</span>
                      : <span className="faint">no ITN</span>}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>

        {booking.reinstatements.length > 0 && (
          <section className="card">
            <div className="card-header">
              <h2>Reinstatement history</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                Same booking, same reference — only the sailing changed
              </span>
            </div>
            <div className="card-body stack">
              {booking.reinstatements.map((entry) => (
                <div key={entry.id} className={styles.sailing}>
                  <div>
                    <div className={styles.sailingLabel}>Left behind</div>
                    <div className={`${styles.sailingValue} ${styles.sailingStale}`}>
                      {entry.previousVessel ?? '—'} · {entry.previousVoyage ?? '—'}
                    </div>
                    <div className="faint">ETD {date(entry.previousEtd)}</div>
                  </div>
                  <div className={styles.sailingArrow} aria-hidden>→</div>
                  <div>
                    <div className={styles.sailingLabel}>Rolled to</div>
                    <div className={styles.sailingValue}>
                      {entry.newVesselName} · {entry.newVoyageNumber}
                    </div>
                    <div className="faint">ETD {date(entry.newEtd)}</div>
                  </div>
                  <div style={{ gridColumn: '1 / -1' }} className="faint">
                    {entry.reason} · {dateTime(entry.reinstatedAt)} · {entry.reinstatedBy}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Audit trail</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                {booking.statusHistory.length} transitions
              </span>
            </div>
            <div className="card-body">
              <Timeline entries={historyEntries} />
            </div>
          </section>

          <section className="card">
            <div className="card-header"><h2>Booking</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Requested ETD" value={date(booking.requestedEtd)} />
                <Item label="Total weight" value={`${number(booking.totalWeightKg, 3)} kg`} />
                <Item label="Customer" value={shortId(booking.customerId)} mono />
                <Item label="Shipper" value={shortId(booking.shipperId)} mono />
                <Item label="Consignee" value={shortId(booking.consigneeId)} mono />
                <Item label="Created" value={`${dateTime(booking.createdAt)} · ${booking.createdBy}`} />
                <Item label="Last change"
                  value={`${dateTime(booking.lastModifiedAt)} · ${booking.lastModifiedBy}`} />
              </dl>
              {booking.quoteId && (
                <p style={{ marginTop: 14, fontSize: 12.5 }}>
                  From quote <Link to={`/quotes/${booking.quoteId}`} className="mono">
                    {shortId(booking.quoteId)}
                  </Link>
                </p>
              )}
              {booking.specialInstructions && (
                <p className={styles.notes}>{booking.specialInstructions}</p>
              )}
            </div>
          </section>
        </div>

        <section className="card">
          <div className="card-header"><h2>Cargo</h2></div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Description</th>
                  <th>HS code</th>
                  <th className="right">Pieces</th>
                  <th className="right">Weight</th>
                  <th className="right">CBM</th>
                </tr>
              </thead>
              <tbody>
                {booking.cargoDetails.map((cargo) => (
                  <tr key={cargo.id}>
                    <td>{cargo.description}</td>
                    <td className="mono">{cargo.hsCode}</td>
                    <td className="right numeric">{cargo.pieces}</td>
                    <td className="right numeric">{number(cargo.weightKg, 3)} kg</td>
                    <td className="right numeric">{number(cargo.cbm, 4)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {dialog === 'submit' && (
        <SubmitModal busy={submit.isPending} mode={booking.shippingMode}
          onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Submitted to carrier', () => submit.mutateAsync(input))} />
      )}
      {dialog === 'confirm' && (
        <ConfirmModal busy={confirm.isPending} isCoLoader={carrier?.bookingSourceType === 'CO_LOADER'}
          requestedEtd={booking.requestedEtd}
          onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Carrier confirmation recorded', () => confirm.mutateAsync(input))} />
      )}
      {dialog === 'reject' && (
        <ReasonModal title="Carrier rejected the booking" label="Reason" busy={reject.isPending}
          confirmLabel="Record rejection" onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Rejection recorded', () => reject.mutateAsync({ reason }))} />
      )}
      {dialog === 'counter' && (
        <CounterOfferModal busy={counterOffer.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Counter-offer recorded', () => counterOffer.mutateAsync(input))} />
      )}
            {dialog === 'reinstate' && (
        <ReinstateModal busy={reinstate.isPending} itnFiled={booking.itnFiled}
          onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Rolled to the new sailing', () => reinstate.mutateAsync(input))} />
      )}
      {dialog === 'cancel' && (
        <CancelModal busy={cancel.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Booking cancelled', () => cancel.mutateAsync(input))} />
      )}
    </>
  )
}

function Item({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? 'mono' : undefined}>{value}</dd>
    </div>
  )
}

function SubmitModal({ busy, mode, onClose, onSubmit }: {
  busy: boolean
  mode: string
  onClose: () => void
  onSubmit: (input: {
    bookingSourceType: BookingSourceType
    carrierId: string
    containerType: ContainerType | null
    numberOfContainers: number | null
  }) => void
}) {
  const isFcl = mode === 'OCEAN_FCL'
  const [sourceType, setSourceType] = useState<BookingSourceType>('DIRECT_CARRIER')
  const [carrierId, setCarrierId] = useState(DEMO_CARRIER_ID)
  const [containerType, setContainerType] = useState<ContainerType>('TWENTY_GP')
  const [containers, setContainers] = useState(1)

  return (
    <Modal title="Submit to carrier"
      description={isFcl
        ? 'Cargo weight is checked against the container payload limit before the request leaves.'
        : 'The request goes to the carrier or co-loader for space.'}
      onClose={onClose}>
      <form className="stack" onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          bookingSourceType: sourceType,
          carrierId,
          containerType: isFcl ? containerType : null,
          numberOfContainers: isFcl ? containers : null,
        })
      }}>
        <div className="form-grid">
          <div className="field">
            <label>Booked through</label>
            <select className="select" value={sourceType}
              onChange={(e) => setSourceType(e.target.value as BookingSourceType)}>
              <option value="DIRECT_CARRIER">Direct carrier</option>
              <option value="CO_LOADER">Co-loader</option>
            </select>
            <span className="hint">Recorded separately — terms and liability differ</span>
          </div>
          <div className="field">
            <label>Carrier id</label>
            <input className="input mono" value={carrierId}
              onChange={(e) => setCarrierId(e.target.value)} required />
          </div>
          {isFcl && (
            <>
              <div className="field">
                <label>Container type</label>
                <select className="select" value={containerType}
                  onChange={(e) => setContainerType(e.target.value as ContainerType)}>
                  <option value="TWENTY_GP">20' GP — max 28,000 kg</option>
                  <option value="FORTY_GP">40' GP — max 26,500 kg</option>
                  <option value="FORTY_HC">40' HC — max 26,500 kg</option>
                  <option value="FORTY_FIVE_HC">45' HC — no published limit</option>
                </select>
              </div>
              <div className="field">
                <label>Containers</label>
                <input type="number" min={1} className="input numeric" value={containers}
                  onChange={(e) => setContainers(Number(e.target.value))} />
              </div>
            </>
          )}
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ConfirmModal({ busy, isCoLoader, requestedEtd, onClose, onSubmit }: {
  busy: boolean
  isCoLoader: boolean
  requestedEtd: string
  onClose: () => void
  onSubmit: (input: {
    carrierBookingRef: string
    coLoaderBookingRef: string | null
    vesselName: string
    voyageNumber: string
    confirmedEtd: string
    confirmedEta: string
  }) => void
}) {
  const [form, setForm] = useState({
    carrierBookingRef: '', coLoaderBookingRef: '',
    vesselName: '', voyageNumber: '',
    confirmedEtd: requestedEtd, confirmedEta: '',
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Record carrier confirmation"
      description="The carrier's reference is externally issued and cannot be recorded before submission."
      onClose={onClose}>
      <form className="stack" onSubmit={(event) => {
        event.preventDefault()
        onSubmit({ ...form, coLoaderBookingRef: form.coLoaderBookingRef || null })
      }}>
        <div className="form-grid">
          <div className="field">
            <label>Carrier booking ref</label>
            <input className="input mono" value={form.carrierBookingRef}
              onChange={(e) => set('carrierBookingRef', e.target.value)} placeholder="MAEU987654" required />
          </div>
          {isCoLoader && (
            <div className="field">
              <label>Co-loader booking ref</label>
              <input className="input mono" value={form.coLoaderBookingRef}
                onChange={(e) => set('coLoaderBookingRef', e.target.value)} required />
              <span className="hint">Required for a co-loader booking</span>
            </div>
          )}
          <div className="field">
            <label>Vessel</label>
            <input className="input" value={form.vesselName}
              onChange={(e) => set('vesselName', e.target.value)} placeholder="MAERSK SEALAND" required />
          </div>
          <div className="field">
            <label>Voyage</label>
            <input className="input mono" value={form.voyageNumber}
              onChange={(e) => set('voyageNumber', e.target.value)} placeholder="024W" required />
          </div>
          <div className="field">
            <label>Confirmed ETD</label>
            <input type="date" className="input" value={form.confirmedEtd}
              onChange={(e) => set('confirmedEtd', e.target.value)} required />
            <span className="hint">More than 2 business days from {date(requestedEtd)} needs customer notice</span>
          </div>
          <div className="field">
            <label>Confirmed ETA</label>
            <input type="date" className="input" value={form.confirmedEta}
              onChange={(e) => set('confirmedEta', e.target.value)} required />
          </div>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record confirmation'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function CounterOfferModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    proposedVessel: string; proposedVoyage: string
    proposedEtd: string; proposedEta: string
  }) => void
}) {
  const [form, setForm] = useState({
    proposedVessel: '', proposedVoyage: '', proposedEtd: '', proposedEta: '',
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Carrier counter-offer" description="An alternative sailing proposed by the carrier."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(form) }}>
        <div className="form-grid">
          <div className="field">
            <label>Proposed vessel</label>
            <input className="input" value={form.proposedVessel}
              onChange={(e) => set('proposedVessel', e.target.value)} required />
          </div>
          <div className="field">
            <label>Proposed voyage</label>
            <input className="input mono" value={form.proposedVoyage}
              onChange={(e) => set('proposedVoyage', e.target.value)} required />
          </div>
          <div className="field">
            <label>Proposed ETD</label>
            <input type="date" className="input" value={form.proposedEtd}
              onChange={(e) => set('proposedEtd', e.target.value)} required />
          </div>
          <div className="field">
            <label>Proposed ETA</label>
            <input type="date" className="input" value={form.proposedEta}
              onChange={(e) => set('proposedEta', e.target.value)} required />
          </div>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record counter-offer'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReinstateModal({ busy, itnFiled, onClose, onSubmit }: {
  busy: boolean
  itnFiled: boolean
  onClose: () => void
  onSubmit: (input: {
    newVesselName: string; newVoyageNumber: string
    newEtd: string; newEta: string; reason: string | null
  }) => void
}) {
  const [form, setForm] = useState({
    newVesselName: '', newVoyageNumber: '', newEtd: '', newEta: '', reason: 'VesselOverbooked',
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Roll to the next sailing"
      description="The booking keeps its reference and identity. The sailing being left behind is recorded before the new one is applied."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(form) }}>
        {itnFiled && (
          <div className={styles.banner + ' ' + styles.bannerWarning} style={{ margin: 0 }}>
            An ITN# is already on file. Rolling the sailing will raise an amendment
            requirement for the Compliance track.
          </div>
        )}
        <div className="form-grid">
          <div className="field">
            <label>New vessel</label>
            <input className="input" value={form.newVesselName}
              onChange={(e) => set('newVesselName', e.target.value)} placeholder="MAERSK KOWLOON" required />
          </div>
          <div className="field">
            <label>New voyage</label>
            <input className="input mono" value={form.newVoyageNumber}
              onChange={(e) => set('newVoyageNumber', e.target.value)} placeholder="026W" required />
          </div>
          <div className="field">
            <label>New ETD</label>
            <input type="date" className="input" value={form.newEtd}
              onChange={(e) => set('newEtd', e.target.value)} required />
            <span className="hint">At least 5 business days out</span>
          </div>
          <div className="field">
            <label>New ETA</label>
            <input type="date" className="input" value={form.newEta}
              onChange={(e) => set('newEta', e.target.value)} required />
          </div>
        </div>
        <div className="field">
          <label>Reason</label>
          <input className="input" value={form.reason} onChange={(e) => set('reason', e.target.value)} />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Rolling…' : 'Roll booking'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function CancelModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { reason: string; initiatedBy: CancellationInitiator }) => void
}) {
  const [reason, setReason] = useState('')
  const [initiatedBy, setInitiatedBy] = useState<CancellationInitiator>('CUSTOMER')

  return (
    <Modal title="Cancel booking"
      description="Who caused the cancellation is recorded — a carrier overbooking can still be rolled forward, a customer cancellation cannot."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit({ reason, initiatedBy }) }}>
        <div className="field">
          <label>Initiated by</label>
          <select className="select" value={initiatedBy}
            onChange={(e) => setInitiatedBy(e.target.value as CancellationInitiator)}>
            <option value="CUSTOMER">Customer</option>
            <option value="CARRIER_OVERBOOKING">Carrier overbooking</option>
            <option value="OPERATIONS">Operations</option>
          </select>
        </div>
        <div className="field">
          <label>Reason</label>
          <textarea className="textarea" value={reason}
            onChange={(e) => setReason(e.target.value)} required />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Keep booking</button>
          <button type="submit" className="btn btn-danger" disabled={busy}>
            {busy ? 'Cancelling…' : 'Cancel booking'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReasonModal({ title, label, placeholder, confirmLabel, busy, onClose, onSubmit }: {
  title: string
  label: string
  placeholder?: string
  confirmLabel: string
  busy: boolean
  onClose: () => void
  onSubmit: (value: string) => void
}) {
  const [value, setValue] = useState('')
  return (
    <Modal title={title} onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(value) }}>
        <div className="field">
          <label>{label}</label>
          <textarea className="textarea" value={value} placeholder={placeholder}
            onChange={(e) => setValue(e.target.value)} required />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}
