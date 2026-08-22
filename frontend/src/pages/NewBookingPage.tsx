import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useCreateBooking } from '../api/bookings'
import type { BookingCargoInput } from '../api/bookings'
import { useQuote } from '../api/quotes'
import type { BookingShippingMode } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/Toast'
import { useFieldErrors } from './useFieldErrors'
import styles from './Form.module.css'

const DEMO = {
  customerId: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  shipperId: 'aabbccdd-1122-3344-5566-778899aabbcc',
  consigneeId: 'bbccddee-2233-4455-6677-8899aabbccdd',
}

const emptyCargo = (): BookingCargoInput => ({
  description: '', hsCode: '', pieces: 1, weightKg: 0,
  lengthCm: 0, widthCm: 0, heightCm: 0,
  hazmat: false, temperatureControlled: false, oversized: false, marksAndNumbers: null,
})

export function NewBookingPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [params] = useSearchParams()
  const fromQuoteId = params.get('quoteId')
  const { data: sourceQuote } = useQuote(fromQuoteId ?? '')
  const createBooking = useCreateBooking()
  const { errorFor, capture, clear } = useFieldErrors()

  const [form, setForm] = useState({
    ...DEMO,
    shippingMode: 'OCEAN_LCL' as BookingShippingMode,
    originPortCode: 'INBOM',
    destinationPortCode: 'SGSIN',
    incoterms: 'FOB',
    requestedEtd: '',
    requestedEta: '',
    transportRequired: true,
    pickupAddress: '',
    specialInstructions: '',
  })
  const [cargo, setCargo] = useState<BookingCargoInput[]>([emptyCargo()])
  const [prefilled, setPrefilled] = useState(false)

  // Copy what the quote can supply. Shipper and consignee are deliberately not
  // carried across: the quote holds those parties only as free-text names for
  // screening, with no party identifiers, so operations must supply them here.
  if (sourceQuote && !prefilled) {
    setPrefilled(true)
    setForm((current) => ({
      ...current,
      originPortCode: sourceQuote.originPortCode,
      destinationPortCode: sourceQuote.destinationPortCode,
      incoterms: sourceQuote.incoterms,
      customerId: sourceQuote.customerId,
      requestedEtd: sourceQuote.requestedEtd ?? '',
      shippingMode: sourceQuote.shippingMode === 'AIR'
        ? 'OCEAN_LCL'
        : (sourceQuote.shippingMode as BookingShippingMode),
    }))
    if (sourceQuote.cargoDetails.length > 0) {
      setCargo(sourceQuote.cargoDetails.map((detail) => ({
        description: detail.description, hsCode: detail.hsCode, pieces: detail.pieces,
        weightKg: detail.weightKg, lengthCm: detail.lengthCm,
        widthCm: detail.widthCm, heightCm: detail.heightCm,
        hazmat: detail.hazmat, temperatureControlled: detail.temperatureControlled,
        oversized: detail.oversized, marksAndNumbers: null,
      })))
    }
  }

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  const setCargoField = <K extends keyof BookingCargoInput>(
    index: number, key: K, value: BookingCargoInput[K],
  ) => setCargo((current) =>
    current.map((line, i) => (i === index ? { ...line, [key]: value } : line)))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    clear()
    try {
      const booking = await createBooking.mutateAsync({
        quoteId: fromQuoteId,
        customerId: form.customerId,
        shipperId: form.shipperId,
        consigneeId: form.consigneeId,
        shippingMode: form.shippingMode,
        originPortCode: form.originPortCode,
        destinationPortCode: form.destinationPortCode,
        incoterms: form.incoterms,
        requestedEtd: form.requestedEtd,
        requestedEta: form.requestedEta || null,
        transportRequired: form.transportRequired,
        pickupAddress: form.transportRequired ? form.pickupAddress : null,
        specialInstructions: form.specialInstructions || null,
        cargoDetails: cargo,
      })
      toast.success(`Booking ${booking.bookingReference} created`, 'Submit it to a carrier to continue.')
      navigate(`/bookings/${booking.id}`)
    } catch (error) {
      capture(error)
      toast.fromError(error, 'Could not create the booking')
    }
  }

  return (
    <>
      <PageHeader
        title="New booking"
        subtitle="Requested ETD must be at least five business days out"
        backTo={{ to: '/bookings', label: 'Bookings' }}
      />

      {sourceQuote && (
        <div className={styles.linked}>
          Prefilled from quote <span className="mono">{sourceQuote.quoteReference}</span>.
          Shipper and consignee identifiers are not carried across — the quote records
          those parties as names for screening only.
        </div>
      )}

      <form onSubmit={submit} className="stack">
        <section className="card">
          <div className="card-header"><h2>Routing</h2></div>
          <div className="card-body">
            <div className="form-grid">
              <FormField label="Mode" error={errorFor('shippingMode')}>
                <select className="select" value={form.shippingMode}
                  onChange={(e) => set('shippingMode', e.target.value as BookingShippingMode)}>
                  <option value="OCEAN_LCL">Ocean LCL</option>
                  <option value="OCEAN_FCL">Ocean FCL</option>
                </select>
              </FormField>
              <FormField label="Origin port" error={errorFor('originPortCode')}>
                <input className="input mono" value={form.originPortCode}
                  onChange={(e) => set('originPortCode', e.target.value.toUpperCase())} required />
              </FormField>
              <FormField label="Destination port" error={errorFor('destinationPortCode')}>
                <input className="input mono" value={form.destinationPortCode}
                  onChange={(e) => set('destinationPortCode', e.target.value.toUpperCase())} required />
              </FormField>
              <FormField label="Incoterms" error={errorFor('incoterms')}>
                <select className="select" value={form.incoterms}
                  onChange={(e) => set('incoterms', e.target.value)}>
                  {['FOB', 'CIF', 'EXW', 'DDP', 'CFR', 'FCA'].map((t) => <option key={t}>{t}</option>)}
                </select>
              </FormField>
              <FormField label="Requested ETD" error={errorFor('requestedEtd')}
                hint="At least 5 business days out">
                <input type="date" className="input" value={form.requestedEtd}
                  onChange={(e) => set('requestedEtd', e.target.value)} required />
              </FormField>
              <FormField label="Requested ETA">
                <input type="date" className="input" value={form.requestedEta}
                  onChange={(e) => set('requestedEta', e.target.value)} />
              </FormField>
            </div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Parties</h2>
            <span className="faint" style={{ fontSize: 12 }}>
              Referenced by id — party management is not built yet
            </span>
          </div>
          <div className="card-body">
            <div className="form-grid">
              <FormField label="Customer id" error={errorFor('customerId')}>
                <input className="input mono" value={form.customerId}
                  onChange={(e) => set('customerId', e.target.value)} required />
              </FormField>
              <FormField label="Shipper id" error={errorFor('shipperId')}>
                <input className="input mono" value={form.shipperId}
                  onChange={(e) => set('shipperId', e.target.value)} required />
              </FormField>
              <FormField label="Consignee id" error={errorFor('consigneeId')}>
                <input className="input mono" value={form.consigneeId}
                  onChange={(e) => set('consigneeId', e.target.value)} required />
              </FormField>
            </div>
          </div>
        </section>

        <section className="card">
          <div className="card-header"><h2>Transport</h2></div>
          <div className="card-body stack">
            <label className="checkbox">
              <input type="checkbox" checked={form.transportRequired}
                onChange={(e) => set('transportRequired', e.target.checked)} />
              Eazy Freight arranges trucking
            </label>
            {form.transportRequired && (
              <FormField label="Pickup address" error={errorFor('pickupAddress')}
                hint="Required when we arrange transport">
                <input className="input" value={form.pickupAddress}
                  onChange={(e) => set('pickupAddress', e.target.value)} required />
              </FormField>
            )}
            <FormField label="Special instructions">
              <textarea className="textarea" value={form.specialInstructions}
                onChange={(e) => set('specialInstructions', e.target.value)} />
            </FormField>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Cargo</h2>
            <button type="button" className="btn btn-sm"
              onClick={() => setCargo((c) => [...c, emptyCargo()])}>Add line</button>
          </div>
          <div className="card-body stack">
            {cargo.map((line, index) => (
              <div key={index} className={styles.repeater}>
                <div className={styles.repeaterHead}>
                  <span className={styles.repeaterTitle}>Line {index + 1}</span>
                  {cargo.length > 1 && (
                    <button type="button" className="btn btn-sm btn-ghost btn-danger"
                      onClick={() => setCargo((c) => c.filter((_, i) => i !== index))}>Remove</button>
                  )}
                </div>
                <div className="form-grid">
                  <FormField label="Description" error={errorFor(`cargoDetails[${index}].description`)}>
                    <input className="input" value={line.description}
                      onChange={(e) => setCargoField(index, 'description', e.target.value)} required />
                  </FormField>
                  <FormField label="HS code" hint="NNNN.NN.NNNN"
                    error={errorFor(`cargoDetails[${index}].hsCode`)}>
                    <input className="input mono" placeholder="8471.30.0100" value={line.hsCode}
                      onChange={(e) => setCargoField(index, 'hsCode', e.target.value)} required />
                  </FormField>
                  <FormField label="Pieces" error={errorFor(`cargoDetails[${index}].pieces`)}>
                    <input type="number" min={1} className="input numeric" value={line.pieces}
                      onChange={(e) => setCargoField(index, 'pieces', Number(e.target.value))} />
                  </FormField>
                  <FormField label="Weight (kg)" error={errorFor(`cargoDetails[${index}].weightKg`)}
                    hint="Checked against container payload at submission">
                    <input type="number" min={0} step="0.001" className="input numeric" value={line.weightKg}
                      onChange={(e) => setCargoField(index, 'weightKg', Number(e.target.value))} />
                  </FormField>
                  <FormField label="Length (cm)">
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.lengthCm}
                      onChange={(e) => setCargoField(index, 'lengthCm', Number(e.target.value))} />
                  </FormField>
                  <FormField label="Width (cm)">
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.widthCm}
                      onChange={(e) => setCargoField(index, 'widthCm', Number(e.target.value))} />
                  </FormField>
                  <FormField label="Height (cm)">
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.heightCm}
                      onChange={(e) => setCargoField(index, 'heightCm', Number(e.target.value))} />
                  </FormField>
                </div>
                <div className="row-wrap" style={{ marginTop: 12 }}>
                  <label className="checkbox">
                    <input type="checkbox" checked={line.hazmat}
                      onChange={(e) => setCargoField(index, 'hazmat', e.target.checked)} /> Hazmat
                  </label>
                  <label className="checkbox">
                    <input type="checkbox" checked={line.temperatureControlled}
                      onChange={(e) => setCargoField(index, 'temperatureControlled', e.target.checked)} />
                    Temperature controlled
                  </label>
                  <label className="checkbox">
                    <input type="checkbox" checked={line.oversized}
                      onChange={(e) => setCargoField(index, 'oversized', e.target.checked)} /> Oversized
                  </label>
                </div>
              </div>
            ))}
          </div>
        </section>

        <div className="form-actions">
          <button type="button" className="btn" onClick={() => navigate('/bookings')}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={createBooking.isPending}>
            {createBooking.isPending ? 'Creating…' : 'Create booking'}
          </button>
        </div>
      </form>
    </>
  )
}

function FormField({ label, hint, error, children }: {
  label: string
  hint?: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {error ? <span className="error">{error}</span> : hint && <span className="hint">{hint}</span>}
    </div>
  )
}
