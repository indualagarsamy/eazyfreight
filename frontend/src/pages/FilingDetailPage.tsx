import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  useAmendFiling, useCancelFiling, useCompileFiling, useCorrectFiling,
  useFiling, useRecordAcceptance, useRecordExportLicense,
  useRecordRejection, useSubmitFiling,
} from '../api/compliance'
import type { CompileEEIDataInput } from '../api/compliance'
import type { EEIFiling } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Lifecycle } from '../components/Lifecycle'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { SimulationBanner } from '../components/SimulationBanner'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { date, dateTime, money, number, titleCase } from '../components/format'
import styles from './Detail.module.css'

const LIFECYCLE = [
  { status: 'DRAFT', label: 'Draft' },
  { status: 'SUBMITTED', label: 'With CBP' },
  { status: 'ACCEPTED', label: 'Accepted' },
]

type ActionKey = 'compile' | 'submit' | 'accept' | 'reject' | 'correct' | 'amend' | 'cancel' | 'license'

function unavailableReason(filing: EEIFiling, action: ActionKey): string | null {
  switch (action) {
    case 'compile':
    case 'license':
      return filing.status === 'DRAFT' ? null : 'The filing is no longer a draft — CBP has seen it'
    case 'submit':
      if (filing.status !== 'DRAFT') return 'Only a draft filing can be submitted'
      if (filing.licenseRequired && !filing.exportLicense) {
        return 'Record the export licence before submitting'
      }
      if (filing.missingRequiredFields.length > 0) {
        return `CBP requires: ${filing.missingRequiredFields.join(', ')}`
      }
      return null
    case 'accept':
    case 'reject':
      return filing.status === 'SUBMITTED' ? null : 'The filing is not awaiting a CBP answer'
    case 'correct':
      return filing.status === 'REJECTED' ? null : 'Only a rejected filing can be corrected'
    case 'amend':
      return filing.status === 'ACCEPTED' ? null : 'Only an accepted filing can be amended'
    case 'cancel':
      return filing.status === 'CANCELLED' ? 'Already cancelled' : null
  }
}

export function FilingDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data: filing, isPending, error, refetch } = useFiling(id)

  const compile = useCompileFiling(id)
  const submit = useSubmitFiling(id)
  const accept = useRecordAcceptance(id)
  const reject = useRecordRejection(id)
  const correct = useCorrectFiling(id)
  const amend = useAmendFiling(id)
  const cancel = useCancelFiling(id)
  const license = useRecordExportLicense(id)

  const [dialog, setDialog] = useState<null | 'compile' | 'accept' | 'reject' | 'amend' | 'cancel' | 'license'>(null)

  if (isPending) return <div className="card"><Skeleton rows={7} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!filing) return null

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      const result = await fn()
      toast.success(label)
      setDialog(null)
      return result
    } catch (err) {
      toast.fromError(err)
      return null
    }
  }

  const Action = ({ action, label, onClick, variant = '' }: {
    action: ActionKey
    label: string
    onClick: () => void
    variant?: string
  }) => (
    <GatedButton label={label} reason={unavailableReason(filing, action)}
      onClick={onClick} variant={variant} size="sm" />
  )

  const historyEntries: TimelineEntry[] = filing.history.map((entry) => ({
    key: entry.id,
    tone: entry.toStatus === 'REJECTED' || entry.toStatus === 'CANCELLED' ? 'danger'
      : entry.toStatus === 'ACCEPTED' ? 'accent' : 'default',
    title: (
      <>
        {entry.fromStatus && entry.fromStatus !== entry.toStatus && (
          <span className="faint">{titleCase(entry.fromStatus)} → </span>
        )}
        {titleCase(entry.toStatus)}
      </>
    ),
    meta: `${dateTime(entry.occurredAt)} · ${entry.actor}`,
    body: entry.detail,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/compliance', label: 'Export compliance' }}
        title={<span className="mono">{filing.filingReference}</span>}
        subtitle={
          <>
            EEI filing for booking{' '}
            <Link to={`/bookings/${filing.bookingId}`} className="mono">
              {filing.bookingId.slice(0, 8)}
            </Link>
          </>
        }
        badges={
          <>
            <StatusPill status={filing.filingType} />
            <StatusPill status={filing.status} />
            {filing.licenseRequired && (
              <StatusPill status="LICENCE" tone="warning" label="Licence required" />
            )}
          </>
        }
        actions={
          <>
            <Action action="compile" label={filing.shipperEin ? 'Edit data' : 'Compile data'}
              onClick={() => setDialog('compile')} />
            <Action action="submit" label="Submit to CBP" variant="btn-primary"
              onClick={() => void run('Submitted to CBP', () => submit.mutateAsync())} />
          </>
        }
      />

      <SimulationBanner />

      {filing.status === 'REJECTED' && (
        <div className={styles.banner}>
          <strong>CBP rejected this filing — code {filing.rejectionReasonCode}.</strong>{' '}
          {filing.rejectionReasonDescription} No ITN was issued. Correct the filing and
          resubmit; the correction is a new filing referencing this one.
        </div>
      )}

      {filing.parentFilingId && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          {filing.filingType === 'AMENDMENT' ? 'Amends' : 'Corrects'}{' '}
          <Link to={`/compliance/${filing.parentFilingId}`} className="mono">
            filing {filing.parentFilingId.slice(0, 8)}
          </Link>
          {filing.amendmentReason && <> — {filing.amendmentReason}</>}
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <Lifecycle steps={LIFECYCLE} current={filing.status} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>CBP response — manual entry from the ACE portal</span>
            <Action action="accept" label="Record acceptance" onClick={() => setDialog('accept')} />
            <Action action="reject" label="Record rejection" onClick={() => setDialog('reject')} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Corrections and changes</span>
            <Action action="correct" label="Correct and refile"
              onClick={() => void run('Correction opened', async () => {
                const created = await correct.mutateAsync()
                navigate(`/compliance/${created.id}`)
                return created
              })} />
            <Action action="amend" label="Amend filing" onClick={() => setDialog('amend')} />
            <Action action="license" label="Record export licence" onClick={() => setDialog('license')} />
            <Action action="cancel" label="Cancel with CBP" variant="btn-danger"
              onClick={() => setDialog('cancel')} />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>ITN records</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                Append-only — an amendment supersedes, never overwrites
              </span>
            </div>
            <div className="card-body">
              {filing.itnRecords.length === 0 ? (
                <p className="muted">
                  No ITN yet. The carrier will not load cargo without one on the shipping
                  documents, and the Documentation track cannot send Master BOL instructions.
                </p>
              ) : (
                <div className="stack" style={{ gap: 10 }}>
                  {filing.itnRecords.map((record) => (
                    <div key={record.id} className={styles.sailing} style={{ gridTemplateColumns: '1fr auto' }}>
                      <div>
                        <div className={styles.sailingLabel}>ITN</div>
                        <div className={`mono ${record.active ? styles.sailingValue : styles.sailingStale}`}>
                          {record.itnNumber}
                        </div>
                        <div className="faint">
                          Issued {dateTime(record.issuedAt)} · recorded by {record.recordedBy}
                        </div>
                      </div>
                      <div className="row" style={{ gap: 6 }}>
                        {record.simulated && (
                          <StatusPill status="SIM" tone="warning" size="sm" label="Simulated" />
                        )}
                        <StatusPill
                          status={record.active ? 'ACTIVE' : 'SUPERSEDED'}
                          tone={record.active ? 'positive' : 'neutral'}
                          size="sm"
                          label={record.active ? 'Active' : 'Superseded'}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-header"><h2>Filing</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Filing required" value={filing.filingRequired ? 'Yes' : 'No'}
                  hint={filing.filingRequired ? undefined : 'At or below $2,500 and no licence'} />
                <Item label="Declared value" value={money(filing.valueUsd)} />
                <Item label="Schedule B" value={filing.scheduleBNumber ?? '—'} mono
                  hint={filing.scheduleBNumber && !filing.scheduleBTranslated
                    ? 'Untranslated HS code' : undefined} />
                <Item label="Commodity" value={filing.commodityDescription ?? '—'} />
                <Item label="Quantity"
                  value={filing.quantityValue === null ? '—'
                    : `${number(filing.quantityValue, 3)} ${filing.quantityUnit ?? ''}`} />
                <Item label="Shipper EIN" value={filing.shipperEin ?? '—'} mono />
                <Item label="Consignee country" value={filing.consigneeCountry ?? '—'} mono />
                <Item label="Carrier SCAC" value={filing.carrierScac ?? '—'} mono />
                <Item label="Vessel"
                  value={filing.vesselName ? `${filing.vesselName} · ${filing.voyageNumber ?? ''}` : '—'} />
                <Item label="Port of export" value={filing.portOfExportCode ?? '—'} mono />
                <Item label="Estimated ETD" value={date(filing.estimatedEtd)} />
                <Item label="AES reference" value={filing.aesSubmissionReference ?? '—'} mono />
              </dl>

              {filing.missingRequiredFields.length > 0 && (
                <p className={styles.declineReason}>
                  <strong>CBP still requires:</strong> {filing.missingRequiredFields.join(', ')}
                </p>
              )}

              {filing.exportLicense && (
                <div className={styles.notes}>
                  <strong>Licence {filing.exportLicense.licenseNumber}</strong> ·{' '}
                  {filing.exportLicense.issuingAuthority} · {filing.exportLicense.licenseType}
                  {filing.exportLicense.commodityEccn && <> · ECCN {filing.exportLicense.commodityEccn}</>}
                  <br />
                  Valid {date(filing.exportLicense.validFrom)} to {date(filing.exportLicense.validUntil)}
                  {filing.exportLicense.valueAuthorized !== null && (
                    <> · authorised {money(filing.exportLicense.valueAuthorized)}</>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>

        <section className="card">
          <div className="card-header">
            <h2>Filing history</h2>
            <span className="faint" style={{ fontSize: 12 }}>
              Append-only, retained five years per CBP regulation
            </span>
          </div>
          <div className="card-body">
            <Timeline entries={historyEntries} />
          </div>
        </section>
      </div>

      {dialog === 'compile' && (
        <CompileModal filing={filing} busy={compile.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('EEI data saved', () => compile.mutateAsync(input))} />
      )}
      {dialog === 'accept' && (
        <AcceptModal busy={accept.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Acceptance recorded', () => accept.mutateAsync(input))} />
      )}
      {dialog === 'reject' && (
        <RejectModal busy={reject.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Rejection recorded', () => reject.mutateAsync(input))} />
      )}
      {dialog === 'amend' && (
        <ReasonModal title="Amend filing"
          description="An amendment is a new filing. When CBP accepts it, the current ITN is superseded and a new one issued."
          label="What changed" confirmLabel="Open amendment" busy={amend.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Amendment opened', async () => {
            const created = await amend.mutateAsync({ reason })
            navigate(`/compliance/${created.id}`)
            return created
          })} />
      )}
      {dialog === 'cancel' && (
        <ReasonModal title="Cancel filing with CBP"
          description="Voids the ITN. An accepted filing left uncancelled leaves an export reported to the US government that never happened."
          label="Reason" confirmLabel="Cancel filing" busy={cancel.isPending} danger
          onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Filing cancelled', () => cancel.mutateAsync({ reason }))} />
      )}
      {dialog === 'license' && (
        <LicenseModal busy={license.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Export licence recorded', () => license.mutateAsync(input))} />
      )}
    </>
  )
}

function Item({ label, value, mono, hint }: {
  label: string; value: string; mono?: boolean; hint?: string
}) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? 'mono' : undefined}>
        {value}
        {hint && <div className="faint" style={{ fontSize: 11.5, marginTop: 2 }}>{hint}</div>}
      </dd>
    </div>
  )
}

function CompileModal({ filing, busy, onClose, onSubmit }: {
  filing: EEIFiling
  busy: boolean
  onClose: () => void
  onSubmit: (input: CompileEEIDataInput) => void
}) {
  const [form, setForm] = useState<CompileEEIDataInput>({
    shipperName: filing.shipperName ?? '',
    shipperEin: filing.shipperEin ?? '',
    shipperAddress: filing.shipperAddress ?? '',
    consigneeName: filing.consigneeName ?? '',
    consigneeAddress: filing.consigneeAddress ?? '',
    consigneeCountry: filing.consigneeCountry ?? '',
    scheduleBNumber: filing.scheduleBNumber ?? '',
    commodityDescription: filing.commodityDescription ?? '',
    quantityValue: filing.quantityValue ?? 1,
    quantityUnit: filing.quantityUnit ?? 'PCS',
    valueUsd: filing.valueUsd ?? 0,
    carrierScac: filing.carrierScac ?? '',
    vesselName: filing.vesselName ?? '',
    voyageNumber: filing.voyageNumber ?? '',
    portOfExportCode: filing.portOfExportCode ?? '',
    countryOfDestination: filing.countryOfDestination ?? '',
    estimatedEtd: filing.estimatedEtd ?? '',
    licenseRequired: filing.licenseRequired,
  })

  const set = <K extends keyof CompileEEIDataInput>(key: K, value: CompileEEIDataInput[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Compile EEI data" width={760}
      description="Shipper EIN, carrier SCAC and consignee country are keyed here because no Party or Carrier context exists — the booking holds only identifiers."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(form) }}>
        <div className="form-grid">
          <F label="Shipper name"><input className="input" value={form.shipperName}
            onChange={(e) => set('shipperName', e.target.value)} required /></F>
          <F label="Shipper EIN" hint="Employer Identification Number"><input className="input mono"
            value={form.shipperEin} placeholder="12-3456789"
            onChange={(e) => set('shipperEin', e.target.value)} required /></F>
          <F label="Shipper address"><input className="input" value={form.shipperAddress ?? ''}
            onChange={(e) => set('shipperAddress', e.target.value)} /></F>
          <F label="Consignee name"><input className="input" value={form.consigneeName}
            onChange={(e) => set('consigneeName', e.target.value)} required /></F>
          <F label="Consignee address"><input className="input" value={form.consigneeAddress ?? ''}
            onChange={(e) => set('consigneeAddress', e.target.value)} /></F>
          <F label="Consignee country" hint="ISO code"><input className="input mono" maxLength={2}
            value={form.consigneeCountry}
            onChange={(e) => set('consigneeCountry', e.target.value.toUpperCase())} required /></F>
          <F label="Schedule B" hint="Blank carries the HS code untranslated">
            <input className="input mono" value={form.scheduleBNumber}
              placeholder="8471.30.0100"
              onChange={(e) => set('scheduleBNumber', e.target.value)} /></F>
          <F label="Commodity description" hint="Max 150 characters">
            <input className="input" maxLength={150} value={form.commodityDescription}
              onChange={(e) => set('commodityDescription', e.target.value)} required /></F>
          <F label="Quantity"><input type="number" step="0.001" min={0} className="input numeric"
            value={form.quantityValue}
            onChange={(e) => set('quantityValue', Number(e.target.value))} /></F>
          <F label="Quantity unit" hint="CBP unit of measure"><input className="input mono"
            value={form.quantityUnit}
            onChange={(e) => set('quantityUnit', e.target.value.toUpperCase())} required /></F>
          <F label="Declared value (USD)" hint="Over $2,500 makes filing mandatory">
            <input type="number" step="0.01" min={0} className="input numeric" value={form.valueUsd}
              onChange={(e) => set('valueUsd', Number(e.target.value))} /></F>
          <F label="Carrier SCAC" hint="Standard Carrier Alpha Code"><input className="input mono"
            maxLength={4} value={form.carrierScac} placeholder="MAEU"
            onChange={(e) => set('carrierScac', e.target.value.toUpperCase())} required /></F>
          <F label="Vessel"><input className="input" value={form.vesselName ?? ''}
            onChange={(e) => set('vesselName', e.target.value)} /></F>
          <F label="Voyage"><input className="input mono" value={form.voyageNumber ?? ''}
            onChange={(e) => set('voyageNumber', e.target.value)} /></F>
          <F label="Port of export" hint="UNLOC"><input className="input mono"
            value={form.portOfExportCode}
            onChange={(e) => set('portOfExportCode', e.target.value.toUpperCase())} required /></F>
          <F label="Country of destination" hint="ISO code"><input className="input mono" maxLength={2}
            value={form.countryOfDestination}
            onChange={(e) => set('countryOfDestination', e.target.value.toUpperCase())} required /></F>
          <F label="Estimated ETD"><input type="date" className="input" value={form.estimatedEtd}
            onChange={(e) => set('estimatedEtd', e.target.value)} required /></F>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={form.licenseRequired}
            onChange={(e) => set('licenseRequired', e.target.checked)} />
          Commodity requires an export licence — blocks submission until one is recorded
        </label>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save EEI data'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function AcceptModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { itnNumber: string; aesSubmissionReference: string | null }) => void
}) {
  const [itnNumber, setItnNumber] = useState('')
  const [ref, setRef] = useState('')
  return (
    <Modal title="Record CBP acceptance"
      description="For an ITN read off the ACE portal. Format is the letter X followed by 14 digits."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault(); onSubmit({ itnNumber, aesSubmissionReference: ref || null })
      }}>
        <F label="ITN number" hint="X followed by 14 digits">
          <input className="input mono" value={itnNumber} placeholder="X20240315123456"
            onChange={(e) => setItnNumber(e.target.value.toUpperCase())} required /></F>
        <F label="AES submission reference">
          <input className="input mono" value={ref} onChange={(e) => setRef(e.target.value)} /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record acceptance'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function RejectModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { rejectionCode: string; rejectionDescription: string }) => void
}) {
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  return (
    <Modal title="Record CBP rejection"
      description="Rejection reasons are retained for audit. No ITN is issued until a corrected filing is accepted."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault(); onSubmit({ rejectionCode: code, rejectionDescription: description })
      }}>
        <F label="CBP rejection code">
          <input className="input mono" value={code} placeholder="127"
            onChange={(e) => setCode(e.target.value)} required /></F>
        <F label="Description">
          <textarea className="textarea" value={description}
            onChange={(e) => setDescription(e.target.value)} required /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-danger" disabled={busy}>
            {busy ? 'Saving…' : 'Record rejection'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function LicenseModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    licenseNumber: string; issuingAuthority: string; licenseType: string
    commodityEccn: string | null; validFrom: string; validUntil: string
    valueAuthorized: number | null
  }) => void
}) {
  const [form, setForm] = useState({
    licenseNumber: '', issuingAuthority: 'BIS', licenseType: 'Individual',
    commodityEccn: '', validFrom: '', validUntil: '', valueAuthorized: 0,
  })
  const set = (key: keyof typeof form, value: string | number) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Record export licence"
      description="Licensable commodities cannot be filed without one." onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          ...form,
          commodityEccn: form.commodityEccn || null,
          valueAuthorized: form.valueAuthorized || null,
        })
      }}>
        <div className="form-grid">
          <F label="Licence number"><input className="input mono" value={form.licenseNumber}
            onChange={(e) => set('licenseNumber', e.target.value)} required /></F>
          <F label="Issuing authority"><input className="input" value={form.issuingAuthority}
            onChange={(e) => set('issuingAuthority', e.target.value)} required /></F>
          <F label="Licence type"><input className="input" value={form.licenseType}
            onChange={(e) => set('licenseType', e.target.value)} required /></F>
          <F label="Commodity ECCN"><input className="input mono" value={form.commodityEccn}
            placeholder="5A002" onChange={(e) => set('commodityEccn', e.target.value)} /></F>
          <F label="Valid from"><input type="date" className="input" value={form.validFrom}
            onChange={(e) => set('validFrom', e.target.value)} required /></F>
          <F label="Valid until"><input type="date" className="input" value={form.validUntil}
            onChange={(e) => set('validUntil', e.target.value)} required /></F>
          <F label="Value authorised (USD)"><input type="number" step="0.01" min={0}
            className="input numeric" value={form.valueAuthorized}
            onChange={(e) => set('valueAuthorized', Number(e.target.value))} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record licence'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReasonModal({ title, description, label, confirmLabel, busy, danger, onClose, onSubmit }: {
  title: string
  description?: string
  label: string
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
          <textarea className="textarea" value={value}
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
