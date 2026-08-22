import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useDispatchInbound, useDispatchOutbound, useLogistics,
  useRecordActualCargo, useRecordContainerNumber, useRecordDeliveredToCustomer,
  useRecordDeliveredToPort, useRecordExaminationHold, useRecordExaminationRelease,
  useRecordLoadedContainerPickedUp, useRecordLoadedOnVessel, useRecordLoadingComplete,
  useRecordSeal, useRecordTerminalReceipt, useRecordTerminalRejection,
  useRecordVesselDeparted, useReplaceSeal,
} from '../api/logistics'
import type { DispatchInput, TerminalReceiptInput } from '../api/logistics'
import { useBooking } from '../api/bookings'
import type { Logistics } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Lifecycle } from '../components/Lifecycle'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ApiError } from '../api/client'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { containerLabel, date, dateTime, money, number, shortId, titleCase } from '../components/format'
import styles from './Detail.module.css'

const DEMO_DRIVER_ID = '9f8e7d6c-5b4a-3c2d-1e0f-9a8b7c6d5e4f'

const LIFECYCLE = [
  { status: 'OUTBOUND_DISPATCHED', label: 'Outbound' },
  { status: 'AT_CUSTOMER', label: 'At customer' },
  { status: 'SEALED', label: 'Sealed' },
  { status: 'INBOUND_DISPATCHED', label: 'Inbound' },
  { status: 'AT_TERMINAL', label: 'At terminal' },
  { status: 'DEPARTED', label: 'Departed' },
]

type ActionKey =
  | 'outbound' | 'containerNumber' | 'deliveredToCustomer' | 'loadingComplete' | 'seal'
  | 'replaceSeal' | 'inbound' | 'pickedUp' | 'deliveredToPort' | 'terminalReceipt'
  | 'terminalRejection' | 'examHold' | 'examRelease' | 'loadedOnVessel' | 'departed'
  | 'actualCargo'

function unavailableReason(l: Logistics, action: ActionKey): string | null {
  const hasOutbound = l.dispatches.some((d) => d.movementType === 'OUTBOUND')
  const hasInbound = l.dispatches.some((d) => d.movementType === 'INBOUND' && d.status !== 'FAILED')
  const inbound = l.dispatches.find((d) => d.movementType === 'INBOUND' && d.status !== 'FAILED')
  const openExam = l.examinations.some((e) => e.open)

  switch (action) {
    case 'outbound':
      return hasOutbound ? 'The outbound truck has already been dispatched' : null
    case 'containerNumber':
      if (l.containerNumber) return 'Container numbers are assigned once and never changed'
      return hasOutbound ? null : 'Dispatch the outbound truck first'
    case 'deliveredToCustomer':
      if (!l.containerNumber) return 'Record the container number first'
      return l.stage === 'OUTBOUND_DISPATCHED' ? null : 'Already delivered'
    case 'loadingComplete':
      return l.stage === 'AT_CUSTOMER' ? null : 'The container is not with the customer'
    case 'seal':
      if (l.activeSealNumber) return 'A seal is already on the container — record a replacement'
      return l.stage === 'LOADING_COMPLETE' ? null : 'Loading is not complete'
    case 'replaceSeal':
      return l.activeSealNumber ? null : 'There is no active seal to replace'
    case 'inbound':
      return l.inboundBlockedReason
    case 'pickedUp':
      if (!hasInbound) return 'Dispatch the inbound truck first'
      return inbound?.status === 'DISPATCHED' ? null : 'Already picked up'
    case 'deliveredToPort':
      return inbound?.status === 'PICKED_UP' ? null : 'The loaded container has not been collected'
    case 'terminalReceipt':
      if (l.terminalAcceptance) return 'A gate receipt is already recorded'
      return hasInbound ? null : 'The container is not on its way to the terminal'
    case 'terminalRejection':
      return hasInbound ? null : 'There is no inbound movement to reject'
    case 'examHold':
      if (openExam) return 'An examination is already open'
      return l.terminalAcceptance ? null : 'The container is not at the terminal'
    case 'examRelease':
      return openExam ? null : 'No examination is open'
    case 'loadedOnVessel':
      if (openExam) return 'The container is under CBP examination'
      return l.terminalAcceptance ? null : 'The terminal has not accepted the container'
    case 'departed':
      return l.loadedOnVesselAt ? null : 'The container is not on the vessel'
    case 'actualCargo':
      return l.loadingCompletedAt ? null : 'Loading is not complete'
  }
}

export function LogisticsDetailPage() {
  const { bookingId = '' } = useParams()
  const toast = useToast()
  const { data: logistics, isPending, error, refetch } = useLogistics(bookingId)
  const { data: booking } = useBooking(bookingId)

  const outbound = useDispatchOutbound(bookingId)
  const inbound = useDispatchInbound(bookingId)
  const containerNumber = useRecordContainerNumber(bookingId)
  const deliveredToCustomer = useRecordDeliveredToCustomer(bookingId)
  const loadingComplete = useRecordLoadingComplete(bookingId)
  const seal = useRecordSeal(bookingId)
  const replaceSeal = useReplaceSeal(bookingId)
  const pickedUp = useRecordLoadedContainerPickedUp(bookingId)
  const deliveredToPort = useRecordDeliveredToPort(bookingId)
  const terminalReceipt = useRecordTerminalReceipt(bookingId)
  const terminalRejection = useRecordTerminalRejection(bookingId)
  const examHold = useRecordExaminationHold(bookingId)
  const examRelease = useRecordExaminationRelease(bookingId)
  const loadedOnVessel = useRecordLoadedOnVessel(bookingId)
  const departed = useRecordVesselDeparted(bookingId)
  const actualCargo = useRecordActualCargo(bookingId)

  const [dialog, setDialog] = useState<
    null | 'outbound' | 'inbound' | 'container' | 'seal' | 'replaceSeal'
    | 'terminal' | 'rejection' | 'examHold' | 'examRelease' | 'cargo'>(null)

  if (isPending) return <div className="card"><Skeleton rows={6} /></div>

  // A 404 here is a normal state: the track simply has not started.
  if (error instanceof ApiError && error.isNotFound) {
    return (
      <>
        <PageHeader
          backTo={{ to: '/logistics', label: 'Container & equipment' }}
          title="Container & equipment"
          subtitle={booking ? `Booking ${booking.bookingReference}` : undefined}
        />
        <div className="card">
          <EmptyState
            title="This booking has no container movements yet"
            detail="The track begins when the outbound truck is sent to the carrier yard for an empty container."
            action={
              <button className="btn btn-primary" onClick={() => setDialog('outbound')}>
                Dispatch outbound truck
              </button>
            }
          />
        </div>
        {dialog === 'outbound' && (
          <DispatchModal
            title="Dispatch outbound truck"
            description="Empty container from the carrier yard to the customer. This is movement one of two."
            defaultPickup="Carrier yard"
            defaultDelivery={booking?.pickupAddress ?? ''}
            busy={outbound.isPending}
            onClose={() => setDialog(null)}
            onSubmit={async (input) => {
              try {
                await outbound.mutateAsync(input)
                toast.success('Outbound truck dispatched')
                setDialog(null)
              } catch (err) {
                toast.fromError(err)
              }
            }}
          />
        )}
      </>
    )
  }

  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!logistics) return null

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
    action: ActionKey; label: string; onClick: () => void; variant?: string
  }) => (
    <GatedButton label={label} reason={unavailableReason(logistics, action)}
      onClick={onClick} variant={variant} size="sm" />
  )

  const sealTimeline: TimelineEntry[] = logistics.sealRecords.map((record) => ({
    key: record.id,
    tone: record.active ? 'accent' : 'default',
    title: (
      <>
        <span className={record.active ? 'mono' : `mono ${styles.sailingStale}`}>
          {record.sealNumber}
        </span>
        {' '}
        <StatusPill status={record.sealSource} size="sm" />
      </>
    ),
    meta: `${dateTime(record.issuedAt)} · ${record.recordedBy}`,
    body: record.active
      ? 'Currently on the container'
      : `Deactivated ${dateTime(record.deactivatedAt)} — ${titleCase(record.deactivationReason ?? '')}`,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/logistics', label: 'Container & equipment' }}
        title={
          <span className="mono">
            {logistics.containerNumber ?? 'Container not yet assigned'}
          </span>
        }
        subtitle={
          <>
            {containerLabel(logistics.containerType)}
            {booking && <> · booking <Link to={`/bookings/${bookingId}`} className="mono">
              {booking.bookingReference}</Link></>}
          </>
        }
        badges={
          <>
            <StatusPill status={logistics.stage} />
            {logistics.documentationPreconditionsMet && (
              <StatusPill status="DOCS" tone="positive" label="Docs ready" />
            )}
          </>
        }
      />

      {/*
        * Only warn while the movement is genuinely stuck. Once the truck has gone,
        * inboundBlockedReason still returns a reason — "already dispatched" — which
        * is a benign state, not something to alarm anyone about.
        */}
      {logistics.inboundBlockedReason && logistics.activeSealNumber
        && !logistics.dispatches.some((d) => d.movementType === 'INBOUND' && d.status !== 'FAILED') && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          <strong>Inbound movement blocked.</strong> {logistics.inboundBlockedReason}. Sending the
          container to the terminal now risks rejection at the gate and storage fees.
        </div>
      )}

      {logistics.terminalAcceptance?.storageFeeApplies && (
        <div className={styles.banner}>
          <strong>Storage fees accruing.</strong> Delivered{' '}
          {logistics.terminalAcceptance.daysEarly} days before the terminal's earliest
          acceptance date of {date(logistics.terminalAcceptance.earliestAcceptanceDate)} —
          about {money(logistics.terminalAcceptance.estimatedStorageFee)} at{' '}
          {money(logistics.terminalAcceptance.storageFeeDailyRate)} per day.
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <Lifecycle steps={LIFECYCLE} current={logistics.stage} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Movement one — empty container out</span>
            <Action action="outbound" label="Dispatch outbound" onClick={() => setDialog('outbound')} />
            <Action action="containerNumber" label="Record container number"
              onClick={() => setDialog('container')} variant="btn-primary" />
            <Action action="deliveredToCustomer" label="Delivered to customer"
              onClick={() => void run('Delivery recorded', () => deliveredToCustomer.mutateAsync())} />
            <Action action="loadingComplete" label="Loading complete"
              onClick={() => void run('Loading complete', () => loadingComplete.mutateAsync())} />
            <Action action="seal" label="Record seal" onClick={() => setDialog('seal')} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Movement two — loaded container to port</span>
            <Action action="inbound" label="Dispatch inbound" onClick={() => setDialog('inbound')}
              variant="btn-primary" />
            <Action action="pickedUp" label="Picked up"
              onClick={() => void run('Pick-up recorded', () => pickedUp.mutateAsync())} />
            <Action action="deliveredToPort" label="Delivered to port"
              onClick={() => void run('Delivery recorded', () => deliveredToPort.mutateAsync())} />
            <Action action="terminalReceipt" label="Terminal gate receipt"
              onClick={() => setDialog('terminal')} />
            <Action action="terminalRejection" label="Terminal rejected"
              onClick={() => setDialog('rejection')} variant="btn-danger" />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Exceptions and departure</span>
            <Action action="examHold" label="CBP hold" onClick={() => setDialog('examHold')} />
            <Action action="examRelease" label="CBP release" onClick={() => setDialog('examRelease')} />
            <Action action="replaceSeal" label="Replace damaged seal"
              onClick={() => setDialog('replaceSeal')} />
            <Action action="actualCargo" label="Record actual cargo" onClick={() => setDialog('cargo')} />
            <Action action="loadedOnVessel" label="Loaded on vessel"
              onClick={() => void run('Loading on vessel recorded',
                () => loadedOnVessel.mutateAsync({ vesselName: null }))} />
            <Action action="departed" label="Vessel departed"
              onClick={() => void run('Departure recorded', () => departed.mutateAsync())} />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Seal history</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                Append-only — a customs re-seal never overwrites
              </span>
            </div>
            <div className="card-body">
              {logistics.sealRecords.length === 0 ? (
                <p className="muted">
                  Not sealed yet. The customer applies the seal at loading and reports the number.
                </p>
              ) : (
                <Timeline entries={sealTimeline} />
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-header"><h2>Container</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Container" value={logistics.containerNumber ?? 'Not yet assigned'} mono />
                <Item label="Type" value={containerLabel(logistics.containerType)} />
                <Item label="Assigned"
                  value={logistics.assignedAt
                    ? `${dateTime(logistics.assignedAt)} · ${logistics.assignedBy}` : '—'} />
                <Item label="Source"
                  value={logistics.source ? titleCase(logistics.source) : '—'} />
                <Item label="ITN"
                  value={logistics.itnNumber ?? 'Awaiting CBP'} mono />
                <Item label="Loading completed" value={dateTime(logistics.loadingCompletedAt)} />
                <Item label="Loaded on vessel" value={dateTime(logistics.loadedOnVesselAt)} />
                <Item label="Vessel departed" value={dateTime(logistics.vesselDepartedAt)} />
              </dl>
            </div>
          </section>
        </div>

        <section className="card">
          <div className="card-header">
            <h2>Truck movements</h2>
            <span className="faint" style={{ fontSize: 12 }}>
              Two dispatches, each with its own driver and receipt
            </span>
          </div>
          {logistics.dispatches.length === 0 ? (
            <div className="card-body"><p className="muted">No dispatches yet.</p></div>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Movement</th>
                    <th>Reference</th>
                    <th>From → to</th>
                    <th>Status</th>
                    <th>Picked up</th>
                    <th>Delivered</th>
                    <th>Receipt</th>
                  </tr>
                </thead>
                <tbody>
                  {logistics.dispatches.map((dispatch) => (
                    <tr key={dispatch.id}>
                      <td><StatusPill status={dispatch.movementType} size="sm"
                        tone={dispatch.movementType === 'OUTBOUND' ? 'neutral' : 'info'} /></td>
                      <td className="mono">{dispatch.tdoReference}</td>
                      <td className="muted">
                        {dispatch.pickupAddress} → {dispatch.deliveryAddress}
                      </td>
                      <td><StatusPill status={dispatch.status} size="sm"
                        tone={dispatch.status === 'FAILED' ? 'danger'
                          : dispatch.status === 'DELIVERED' ? 'positive' : 'info'} /></td>
                      <td className="muted">{dateTime(dispatch.actualPickupDate)}</td>
                      <td className="muted">{dateTime(dispatch.actualDeliveryDate)}</td>
                      <td className="mono">{dispatch.deliveryReceiptReference ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header"><h2>Terminal</h2></div>
            <div className="card-body">
              {!logistics.terminalAcceptance ? (
                <p className="muted">The terminal has not accepted the container yet.</p>
              ) : (
                <dl className="definition-list">
                  <Item label="Gate receipt"
                    value={logistics.terminalAcceptance.gateReceiptNumber} mono />
                  <Item label="Terminal" value={logistics.terminalAcceptance.terminalName} />
                  <Item label="Accepted" value={dateTime(logistics.terminalAcceptance.acceptedAt)} />
                  <Item label="Earliest acceptance"
                    value={date(logistics.terminalAcceptance.earliestAcceptanceDate)} />
                  <Item label="Vessel cut-off"
                    value={date(logistics.terminalAcceptance.vesselCutOffDate)} />
                  <Item label="Storage fee"
                    value={logistics.terminalAcceptance.storageFeeApplies
                      ? `${money(logistics.terminalAcceptance.estimatedStorageFee)} (${logistics.terminalAcceptance.daysEarly} days early)`
                      : 'None'} />
                </dl>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <h2>Actual cargo</h2>
              {logistics.actualCargoDetails?.divergesMaterially && (
                <StatusPill status="VARIANCE" tone="warning" size="sm" label="Amendment owed" />
              )}
            </div>
            <div className="card-body">
              {!logistics.actualCargoDetails ? (
                <p className="muted">
                  Not recorded yet. Actuals confirmed at loading flow onto the House BOL and the
                  invoice, and oblige an EEI amendment if they diverge from what was filed.
                </p>
              ) : (
                <dl className="definition-list">
                  <Item label="Actual weight"
                    value={`${number(logistics.actualCargoDetails.actualWeightKg, 3)} kg`} />
                  <Item label="Booked weight"
                    value={`${number(logistics.actualCargoDetails.bookedWeightKg, 3)} kg`} />
                  <Item label="Variance"
                    value={`${number(logistics.actualCargoDetails.weightVarianceKg, 3)} kg`} />
                  <Item label="Actual pieces"
                    value={String(logistics.actualCargoDetails.actualPieces)} />
                  <Item label="Booked pieces"
                    value={String(logistics.actualCargoDetails.bookedPieces)} />
                  <Item label="Actual CBM"
                    value={number(logistics.actualCargoDetails.actualCbm, 4)} />
                </dl>
              )}
            </div>
          </section>
        </div>

        {logistics.examinations.length > 0 && (
          <section className="card">
            <div className="card-header"><h2>CBP examinations</h2></div>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Hold placed</th>
                    <th>Completed</th>
                    <th>Result</th>
                    <th>Officer</th>
                    <th>Seal replaced</th>
                    <th>Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {logistics.examinations.map((exam) => (
                    <tr key={exam.id}>
                      <td>{dateTime(exam.holdPlacedAt)}</td>
                      <td className="muted">{dateTime(exam.examinationCompletedAt)}</td>
                      <td>{exam.result
                        ? <StatusPill status={exam.result} size="sm"
                          tone={exam.result === 'RELEASED' ? 'positive' : 'danger'} />
                        : <StatusPill status="OPEN" tone="warning" size="sm" label="Open" />}</td>
                      <td className="mono muted">{exam.cbpOfficerId ?? '—'}</td>
                      <td className="mono muted">{shortId(exam.replacementSealId)}</td>
                      <td className="muted">{exam.notes ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>

      {dialog === 'outbound' && (
        <DispatchModal title="Dispatch outbound truck"
          description="Empty container from the carrier yard to the customer. Movement one of two."
          defaultPickup="Carrier yard" defaultDelivery={booking?.pickupAddress ?? ''}
          busy={outbound.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Outbound truck dispatched',
            () => outbound.mutateAsync(input))} />
      )}
      {dialog === 'inbound' && (
        <DispatchModal title="Dispatch inbound truck"
          description="Loaded, sealed container to the port terminal. Refused until the ITN is in hand."
          defaultPickup={booking?.pickupAddress ?? ''} defaultDelivery="Port terminal"
          busy={inbound.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Inbound truck dispatched',
            () => inbound.mutateAsync(input))} />
      )}
      {dialog === 'container' && (
        <TextModal title="Record container number"
          description="Assigned at the carrier yard when the driver arrives. It cannot be changed afterwards."
          label="Container number" placeholder="MSCU1234567" confirmLabel="Record"
          busy={containerNumber.isPending} onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Container number recorded',
            () => containerNumber.mutateAsync({ containerNumber: value, source: 'CARRIER_YARD' }))} />
      )}
      {dialog === 'seal' && (
        <TextModal title="Record seal number"
          description="Applied by the customer at loading and reported to us."
          label="Seal number" placeholder="SEAL123456" confirmLabel="Record"
          busy={seal.isPending} onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Seal recorded',
            () => seal.mutateAsync({ sealNumber: value }))} />
      )}
      {dialog === 'replaceSeal' && (
        <TextModal title="Replace damaged seal"
          description="The previous seal is kept on record with its reason, not overwritten."
          label="New seal number" confirmLabel="Replace"
          busy={replaceSeal.isPending} onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Seal replaced',
            () => replaceSeal.mutateAsync({ sealNumber: value }))} />
      )}
      {dialog === 'terminal' && (
        <TerminalModal busy={terminalReceipt.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Gate receipt recorded',
            () => terminalReceipt.mutateAsync(input))} />
      )}
      {dialog === 'rejection' && (
        <TextModal title="Terminal rejected the container" label="Reason"
          confirmLabel="Record rejection" danger busy={terminalRejection.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Rejection recorded',
            () => terminalRejection.mutateAsync({ reason: value }))} />
      )}
      {dialog === 'examHold' && (
        <TextModal title="CBP examination hold" label="CBP officer or case reference"
          confirmLabel="Record hold" busy={examHold.isPending} onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Hold recorded',
            () => examHold.mutateAsync({ cbpOfficerId: value, notes: null }))} />
      )}
      {dialog === 'examRelease' && (
        <TextModal title="CBP examination release"
          description="CBP cut the original seal, so a replacement number is required."
          label="Replacement seal number" confirmLabel="Record release"
          busy={examRelease.isPending} onClose={() => setDialog(null)}
          onSubmit={(value) => void run('Release recorded',
            () => examRelease.mutateAsync({
              result: 'RELEASED', replacementSealNumber: value, notes: null,
            }))} />
      )}
      {dialog === 'cargo' && (
        <ActualCargoModal busy={actualCargo.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Actual cargo recorded',
            () => actualCargo.mutateAsync(input))} />
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

function DispatchModal({ title, description, defaultPickup, defaultDelivery, busy, onClose, onSubmit }: {
  title: string
  description: string
  defaultPickup: string
  defaultDelivery: string
  busy: boolean
  onClose: () => void
  onSubmit: (input: DispatchInput) => void
}) {
  const [ownFleet, setOwnFleet] = useState(true)
  const [form, setForm] = useState({
    driverId: DEMO_DRIVER_ID,
    truckingVendorId: '',
    vehicleReference: '',
    pickupAddress: defaultPickup,
    deliveryAddress: defaultDelivery,
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title={title} description={description} onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          driverId: ownFleet ? form.driverId : null,
          truckingVendorId: ownFleet ? null : form.truckingVendorId,
          vehicleReference: ownFleet ? null : form.vehicleReference,
          pickupAddress: form.pickupAddress,
          deliveryAddress: form.deliveryAddress,
          scheduledPickupDate: null,
          scheduledDeliveryDate: null,
        })
      }}>
        <label className="checkbox">
          <input type="checkbox" checked={ownFleet} onChange={(e) => setOwnFleet(e.target.checked)} />
          Own fleet driver
        </label>
        <div className="form-grid">
          {ownFleet ? (
            <F label="Driver id">
              <input className="input mono" value={form.driverId}
                onChange={(e) => set('driverId', e.target.value)} required /></F>
          ) : (
            <>
              <F label="Trucking vendor id">
                <input className="input mono" value={form.truckingVendorId}
                  onChange={(e) => set('truckingVendorId', e.target.value)} required /></F>
              <F label="Vehicle reference" hint="Required for audit when a vendor is used">
                <input className="input" value={form.vehicleReference}
                  onChange={(e) => set('vehicleReference', e.target.value)} required /></F>
            </>
          )}
          <F label="Pickup address">
            <input className="input" value={form.pickupAddress}
              onChange={(e) => set('pickupAddress', e.target.value)} required /></F>
          <F label="Delivery address">
            <input className="input" value={form.deliveryAddress}
              onChange={(e) => set('deliveryAddress', e.target.value)} required /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Dispatching…' : 'Dispatch'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function TerminalModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: TerminalReceiptInput) => void
}) {
  const [form, setForm] = useState({
    gateReceiptNumber: '', terminalName: '',
    earliestAcceptanceDate: '', vesselCutOffDate: '', storageFeeDailyRate: '',
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Terminal gate receipt"
      description="Delivering before the earliest acceptance date starts storage fees accruing per diem."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          gateReceiptNumber: form.gateReceiptNumber,
          terminalName: form.terminalName,
          earliestAcceptanceDate: form.earliestAcceptanceDate || null,
          vesselCutOffDate: form.vesselCutOffDate || null,
          storageFeeDailyRate: form.storageFeeDailyRate ? Number(form.storageFeeDailyRate) : null,
        })
      }}>
        <div className="form-grid">
          <F label="Gate receipt number">
            <input className="input mono" value={form.gateReceiptNumber}
              onChange={(e) => set('gateReceiptNumber', e.target.value)} required /></F>
          <F label="Terminal name">
            <input className="input" value={form.terminalName}
              onChange={(e) => set('terminalName', e.target.value)} required /></F>
          <F label="Earliest acceptance date" hint="Before this, storage fees apply">
            <input type="date" className="input" value={form.earliestAcceptanceDate}
              onChange={(e) => set('earliestAcceptanceDate', e.target.value)} /></F>
          <F label="Vessel cut-off date">
            <input type="date" className="input" value={form.vesselCutOffDate}
              onChange={(e) => set('vesselCutOffDate', e.target.value)} /></F>
          <F label="Storage fee per day (USD)">
            <input type="number" step="0.01" min={0} className="input numeric"
              value={form.storageFeeDailyRate}
              onChange={(e) => set('storageFeeDailyRate', e.target.value)} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record receipt'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ActualCargoModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { actualWeightKg: number; actualPieces: number; actualCbm: number | null }) => void
}) {
  const [form, setForm] = useState({ actualWeightKg: 0, actualPieces: 1, actualCbm: 0 })
  return (
    <Modal title="Record actual cargo"
      description="Confirmed at loading. If these diverge materially from the booking and the EEI is already filed, CBP is owed an amendment."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          actualWeightKg: form.actualWeightKg,
          actualPieces: form.actualPieces,
          actualCbm: form.actualCbm || null,
        })
      }}>
        <div className="form-grid">
          <F label="Actual weight (kg)">
            <input type="number" step="0.001" min={0} className="input numeric"
              value={form.actualWeightKg}
              onChange={(e) => setForm((c) => ({ ...c, actualWeightKg: Number(e.target.value) }))} /></F>
          <F label="Actual pieces">
            <input type="number" min={1} className="input numeric" value={form.actualPieces}
              onChange={(e) => setForm((c) => ({ ...c, actualPieces: Number(e.target.value) }))} /></F>
          <F label="Actual CBM">
            <input type="number" step="0.0001" min={0} className="input numeric" value={form.actualCbm}
              onChange={(e) => setForm((c) => ({ ...c, actualCbm: Number(e.target.value) }))} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record actuals'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function TextModal({ title, description, label, placeholder, confirmLabel, busy, danger, onClose, onSubmit }: {
  title: string
  description?: string
  label: string
  placeholder?: string
  confirmLabel: string
  busy: boolean
  danger?: boolean
  onClose: () => void
  onSubmit: (value: string) => void
}) {
  const [value, setValue] = useState('')
  return (
    <Modal title={title} description={description} onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(value) }}>
        <F label={label}>
          <input className="input mono" value={value} placeholder={placeholder}
            onChange={(e) => setValue(e.target.value)} required /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} disabled={busy}>
            {busy ? 'Saving…' : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function F({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}
