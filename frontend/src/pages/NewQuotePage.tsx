import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateQuote } from '../api/quotes'
import type { CargoDetailInput } from '../api/quotes'
import type { QuoteShippingMode } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/Toast'
import { useFieldErrors } from './useFieldErrors'
import styles from './Form.module.css'

const DEMO_CUSTOMER_ID = '7c9e6679-7425-40de-944b-e07fc1f90ae7'

const emptyCargo = (): CargoDetailInput => ({
  description: '', hsCode: '', pieces: 1, weightKg: 0,
  lengthCm: 0, widthCm: 0, heightCm: 0,
  hazmat: false, temperatureControlled: false, oversized: false,
})

export function NewQuotePage() {
  const navigate = useNavigate()
  const toast = useToast()
  const createQuote = useCreateQuote()
  const { errorFor, capture, clear } = useFieldErrors()

  const [form, setForm] = useState({
    customerId: DEMO_CUSTOMER_ID,
    shippingMode: 'OCEAN_LCL' as QuoteShippingMode,
    originPortCode: 'INBOM',
    destinationPortCode: 'SGSIN',
    incoterms: 'FOB',
    requestedEtd: '',
    currency: 'USD',
    insuranceRequired: false,
    specialHandling: '',
    shipperName: '',
    shipperAddress: '',
    shipperCountry: '',
    consigneeName: '',
    consigneeAddress: '',
    consigneeCountry: '',
  })
  const [cargo, setCargo] = useState<CargoDetailInput[]>([emptyCargo()])

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  const setCargoField = <K extends keyof CargoDetailInput>(
    index: number, key: K, value: CargoDetailInput[K],
  ) => setCargo((current) =>
    current.map((line, i) => (i === index ? { ...line, [key]: value } : line)))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    clear()
    try {
      const quote = await createQuote.mutateAsync({
        ...form,
        requestedEtd: form.requestedEtd || null,
        specialHandling: form.specialHandling || null,
        cargoDetails: cargo,
      })
      toast.success(
        `Quote ${quote.quoteReference} created`,
        quote.screeningStatus === 'FLAGGED'
          ? 'Denied party screening flagged this quote — it cannot be sent.'
          : 'Denied party screening cleared. Price it to continue.',
      )
      navigate(`/quotes/${quote.id}`)
    } catch (error) {
      if (!capture(error)) toast.fromError(error, 'Could not create the quote')
      else toast.fromError(error)
    }
  }

  return (
    <>
      <PageHeader
        title="New quote"
        subtitle="Logging the enquiry runs denied party screening straight away — a quote cannot be sent until it clears"
        backTo={{ to: '/quotes', label: 'Quotes' }}
      />

      <form onSubmit={submit} className="stack">
        <section className="card">
          <div className="card-header"><h2>Routing</h2></div>
          <div className="card-body">
            <div className="form-grid">
              <Field label="Mode" error={errorFor('shippingMode')}>
                <select
                  className="select"
                  value={form.shippingMode}
                  onChange={(e) => set('shippingMode', e.target.value as QuoteShippingMode)}
                >
                  <option value="OCEAN_LCL">Ocean LCL</option>
                  <option value="OCEAN_FCL">Ocean FCL</option>
                  <option value="AIR">Air</option>
                </select>
              </Field>
              <Field label="Origin port" hint="UNLOC or IATA code" error={errorFor('originPortCode')}>
                <input className="input mono" value={form.originPortCode}
                  onChange={(e) => set('originPortCode', e.target.value.toUpperCase())} required />
              </Field>
              <Field label="Destination port" hint="Must differ from origin"
                error={errorFor('destinationPortCode')}>
                <input className="input mono" value={form.destinationPortCode}
                  onChange={(e) => set('destinationPortCode', e.target.value.toUpperCase())} required />
              </Field>
              <Field label="Incoterms" error={errorFor('incoterms')}>
                <select className="select" value={form.incoterms}
                  onChange={(e) => set('incoterms', e.target.value)}>
                  {['FOB', 'CIF', 'EXW', 'DDP', 'CFR', 'FCA'].map((term) => (
                    <option key={term} value={term}>{term}</option>
                  ))}
                </select>
              </Field>
              <Field label="Requested ETD" hint="At least 5 business days out"
                error={errorFor('requestedEtd')}>
                <input type="date" className="input" value={form.requestedEtd}
                  onChange={(e) => set('requestedEtd', e.target.value)} />
              </Field>
              <Field label="Currency" error={errorFor('currency')}>
                <input className="input mono" value={form.currency} maxLength={3}
                  onChange={(e) => set('currency', e.target.value.toUpperCase())} required />
              </Field>
            </div>
            <label className="checkbox" style={{ marginTop: 16 }}>
              <input type="checkbox" checked={form.insuranceRequired}
                onChange={(e) => set('insuranceRequired', e.target.checked)} />
              Cargo insurance requested
            </label>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Parties</h2>
            <span className="faint" style={{ fontSize: 12 }}>Screened against OFAC and CBP denied party lists</span>
          </div>
          <div className="card-body stack">
            <div className="form-grid">
              <Field label="Shipper name" error={errorFor('shipperName')}>
                <input className="input" value={form.shipperName}
                  onChange={(e) => set('shipperName', e.target.value)} required />
              </Field>
              <Field label="Shipper address">
                <input className="input" value={form.shipperAddress}
                  onChange={(e) => set('shipperAddress', e.target.value)} />
              </Field>
              <Field label="Shipper country" hint="ISO code">
                <input className="input mono" value={form.shipperCountry} maxLength={2}
                  onChange={(e) => set('shipperCountry', e.target.value.toUpperCase())} />
              </Field>
            </div>
            <div className="form-grid">
              <Field label="Consignee name" error={errorFor('consigneeName')}>
                <input className="input" value={form.consigneeName}
                  onChange={(e) => set('consigneeName', e.target.value)} required />
              </Field>
              <Field label="Consignee address">
                <input className="input" value={form.consigneeAddress}
                  onChange={(e) => set('consigneeAddress', e.target.value)} />
              </Field>
              <Field label="Consignee country" hint="ISO code">
                <input className="input mono" value={form.consigneeCountry} maxLength={2}
                  onChange={(e) => set('consigneeCountry', e.target.value.toUpperCase())} />
              </Field>
            </div>
          </div>
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Cargo</h2>
            <button type="button" className="btn btn-sm"
              onClick={() => setCargo((c) => [...c, emptyCargo()])}>
              Add line
            </button>
          </div>
          <div className="card-body stack">
            {cargo.map((line, index) => (
              <div key={index} className={styles.repeater}>
                <div className={styles.repeaterHead}>
                  <span className={styles.repeaterTitle}>Line {index + 1}</span>
                  {cargo.length > 1 && (
                    <button type="button" className="btn btn-sm btn-ghost btn-danger"
                      onClick={() => setCargo((c) => c.filter((_, i) => i !== index))}>
                      Remove
                    </button>
                  )}
                </div>
                <div className="form-grid">
                  <Field label="Description" error={errorFor(`cargoDetails[${index}].description`)}>
                    <input className="input" value={line.description}
                      onChange={(e) => setCargoField(index, 'description', e.target.value)} required />
                  </Field>
                  <Field label="HS code" hint="NNNN.NN.NNNN"
                    error={errorFor(`cargoDetails[${index}].hsCode`)}>
                    <input className="input mono" placeholder="8471.30.0100" value={line.hsCode}
                      onChange={(e) => setCargoField(index, 'hsCode', e.target.value)} required />
                  </Field>
                  <Field label="Pieces" error={errorFor(`cargoDetails[${index}].pieces`)}>
                    <input type="number" min={1} className="input numeric" value={line.pieces}
                      onChange={(e) => setCargoField(index, 'pieces', Number(e.target.value))} />
                  </Field>
                  <Field label="Weight (kg)" error={errorFor(`cargoDetails[${index}].weightKg`)}>
                    <input type="number" min={0} step="0.001" className="input numeric" value={line.weightKg}
                      onChange={(e) => setCargoField(index, 'weightKg', Number(e.target.value))} />
                  </Field>
                  <Field label="Length (cm)" error={errorFor(`cargoDetails[${index}].lengthCm`)}>
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.lengthCm}
                      onChange={(e) => setCargoField(index, 'lengthCm', Number(e.target.value))} />
                  </Field>
                  <Field label="Width (cm)" error={errorFor(`cargoDetails[${index}].widthCm`)}>
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.widthCm}
                      onChange={(e) => setCargoField(index, 'widthCm', Number(e.target.value))} />
                  </Field>
                  <Field label="Height (cm)" error={errorFor(`cargoDetails[${index}].heightCm`)}>
                    <input type="number" min={0} step="0.01" className="input numeric" value={line.heightCm}
                      onChange={(e) => setCargoField(index, 'heightCm', Number(e.target.value))} />
                  </Field>
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
                <p className={styles.note}>
                  Chargeable weight is derived by the service from these dimensions and the mode.
                </p>
              </div>
            ))}
          </div>
        </section>

        <div className="form-actions">
          <button type="button" className="btn" onClick={() => navigate('/quotes')}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={createQuote.isPending}>
            {createQuote.isPending ? 'Creating…' : 'Create quote'}
          </button>
        </div>
      </form>
    </>
  )
}

function Field({ label, hint, error, children }: {
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
