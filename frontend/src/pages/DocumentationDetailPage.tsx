import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  useAmendHouseBol, useDistribute, useGeneratePdf, useHouseBol,
  useReleaseOriginals, useRevisions, useSurrenderOriginals, useVoidHouseBol,
} from '../api/documentation'
import type { HouseBOL } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { dateTime, number, titleCase } from '../components/format'
import styles from './Detail.module.css'

const RELEASE_NOTES: Record<string, string> = {
  ORIGINAL_BOL: 'Three negotiable originals. Whoever holds one controls the cargo, and one must be surrendered at destination.',
  TELEX_RELEASE: 'No original issued. The shipper surrenders their claim electronically and the destination agent releases to the named consignee.',
  SEA_WAYBILL: 'Non-negotiable. The named consignee collects on proof of identity — nothing to surrender.',
}

type ActionKey = 'pdf' | 'distribute' | 'releaseOriginals' | 'surrender' | 'amend' | 'void'

function unavailableReason(bol: HouseBOL, action: ActionKey): string | null {
  switch (action) {
    case 'pdf':
    case 'distribute':
      return bol.active ? null : 'This revision has been superseded'
    case 'releaseOriginals':
      if (bol.releaseType !== 'ORIGINAL_BOL') {
        return `Originals only exist for an Original BOL release (this is ${titleCase(bol.releaseType)})`
      }
      if (!bol.active) return 'This revision has been superseded'
      return bol.originals ? 'Originals have already been released' : null
    case 'surrender':
      if (!bol.originals) return 'No originals have been released'
      return bol.originals.allSurrendered ? 'All originals are already back' : null
    case 'amend':
      return bol.amendmentBlockedReason
    case 'void':
      return bol.active ? null : 'Already voided'
  }
}

export function DocumentationDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data: bol, isPending, error, refetch } = useHouseBol(id)
  const { data: revisions } = useRevisions(bol?.houseBolNumber ?? '')

  const pdf = useGeneratePdf()
  const distribute = useDistribute()
  const releaseOriginals = useReleaseOriginals()
  const surrender = useSurrenderOriginals()
  const amend = useAmendHouseBol()
  const voidBol = useVoidHouseBol()

  const [dialog, setDialog] = useState<
    null | 'distribute' | 'release' | 'surrender' | 'amend' | 'void'>(null)

  if (isPending) return <div className="card"><Skeleton rows={7} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!bol) return null

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
    <GatedButton label={label} reason={unavailableReason(bol, action)}
      onClick={onClick} variant={variant} size="sm" />
  )

  const revisionEntries: TimelineEntry[] = (revisions ?? []).map((rev) => ({
    key: rev.id,
    tone: rev.active ? 'accent' : 'default',
    title: (
      <>
        <span className={rev.active ? undefined : styles.sailingStale}>
          Revision {rev.revisionNumber}
        </span>
        {' '}
        <StatusPill status={rev.status} size="sm"
          tone={rev.active ? 'positive' : 'neutral'}
          label={rev.active ? 'Active' : 'Voided'} />
      </>
    ),
    meta: `${dateTime(rev.issuedAt)} · ${rev.issuedBy}`,
    body: (
      <>
        {rev.amendmentReason ?? 'Original issue'}
        {rev.id !== bol.id && (
          <> · <Link to={`/documentation/${rev.id}`}>view</Link></>
        )}
      </>
    ),
  }))

  const distributionEntries: TimelineEntry[] = bol.distributions.map((d) => ({
    key: d.id,
    title: <>{titleCase(d.recipient)} — {d.recipientName ?? 'unnamed'}</>,
    meta: `${dateTime(d.sentAt)} · ${d.sentBy} · ${titleCase(d.channel)}`,
    body: `Revision ${d.revisionNumber}`,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/documentation', label: 'Documentation' }}
        title={<span className="mono">{bol.houseBolNumber}</span>}
        subtitle={
          <>
            Revision {bol.revisionNumber} · booking{' '}
            <Link to={`/bookings/${bol.bookingId}`} className="mono">
              {bol.bookingId.slice(0, 8)}
            </Link>
          </>
        }
        badges={
          <>
            <StatusPill status={bol.status} size="md"
              tone={bol.active ? 'positive' : 'neutral'}
              label={bol.active ? 'Active' : 'Voided'} />
            <StatusPill status={bol.releaseType} />
            {bol.originals && !bol.originals.allSurrendered && (
              <StatusPill status="ORIG" tone="warning"
                label={`${bol.originals.outstanding} originals out`} />
            )}
          </>
        }
        actions={
          <>
            <Action action="pdf" label="Generate PDF"
              onClick={() => void run('PDF generated', () => pdf.mutateAsync(bol.id))} />
            <Action action="distribute" label="Send" variant="btn-primary"
              onClick={() => setDialog('distribute')} />
          </>
        }
      />

      {!bol.active && (
        <div className={styles.banner}>
          <strong>This revision is voided.</strong>{' '}
          {bol.supersededByHouseBolId ? (
            <>Superseded by{' '}
              <Link to={`/documentation/${bol.supersededByHouseBolId}`}>a later revision</Link>.
              It is kept because it may still be the copy a consignee is holding.</>
          ) : 'It was voided without a replacement.'}
        </div>
      )}

      {bol.amendmentBlockedReason && bol.active && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          <strong>Amendment blocked.</strong> {bol.amendmentBlockedReason}.
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <p className="muted" style={{ fontSize: 13 }}>
              <strong style={{ color: 'var(--text)' }}>{titleCase(bol.releaseType)}.</strong>{' '}
              {RELEASE_NOTES[bol.releaseType]}
            </p>
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Originals</span>
            <Action action="releaseOriginals" label="Release originals to courier"
              onClick={() => setDialog('release')} />
            <Action action="surrender" label="Record originals surrendered"
              onClick={() => setDialog('surrender')} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Amendment</span>
            <Action action="amend" label="Amend — new revision" onClick={() => setDialog('amend')} />
            <Action action="void" label="Void without replacement"
              onClick={() => setDialog('void')} variant="btn-danger" />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Revision history</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                The number is stable; revisions accumulate
              </span>
            </div>
            <div className="card-body">
              <Timeline entries={revisionEntries} />
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <h2>Parties as issued</h2>
              <span className="faint" style={{ fontSize: 12 }}>Snapshot, not a live reference</span>
            </div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Shipper" value={bol.shipperNameSnapshot}
                  hint={bol.shipperAddressSnapshot ?? undefined} />
                <Item label="Consignee" value={bol.consigneeNameSnapshot}
                  hint={bol.consigneeAddressSnapshot ?? undefined} />
                <Item label="Notify party" value={bol.notifyPartyNameSnapshot ?? '—'}
                  hint={bol.notifyPartyAddressSnapshot ?? undefined} />
              </dl>
              <p className="faint" style={{ marginTop: 12, fontSize: 11.5 }}>
                These were copied in at issuance. A later change to the customer record does
                not alter an issued bill of lading.
              </p>
            </div>
          </section>
        </div>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header"><h2>Carriage and cargo</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Container" value={bol.containerNumber} mono />
                <Item label="Seal" value={bol.sealNumber} mono />
                <Item label="Vessel" value={bol.vesselName ?? '—'} />
                <Item label="Voyage" value={bol.voyageNumber ?? '—'} />
                <Item label="Loading" value={bol.portOfLoadingCode} mono />
                <Item label="Discharge" value={bol.portOfDischargeCode} mono />
                <Item label="Cargo" value={bol.cargoDescription} />
                <Item label="HS code" value={bol.hsCode ?? '—'} mono />
                <Item label="Weight"
                  value={bol.weightKg === null ? '—' : `${number(bol.weightKg, 3)} kg`} />
                <Item label="Pieces" value={bol.pieces === null ? '—' : String(bol.pieces)} />
                <Item label="Freight terms" value={titleCase(bol.freightTerms)} />
                <Item label="PDF" value={bol.pdfReference ?? 'Not generated'} mono />
              </dl>
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <h2>Distribution</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                {bol.distributions.length} sent
              </span>
            </div>
            <div className="card-body">
              {bol.distributions.length === 0 ? (
                <p className="muted">
                  Not sent to anyone yet. Each send records the recipient, the channel and which
                  revision they received.
                </p>
              ) : (
                <Timeline entries={distributionEntries} />
              )}

              {bol.originals && (
                <div className={styles.notes} style={{ marginTop: 14 }}>
                  <strong>Originals</strong> — {bol.originals.originalsSurrendered} of{' '}
                  {bol.originals.originalsIssued} surrendered
                  {bol.originals.releasedTo && <> · released to {bol.originals.releasedTo}</>}
                  {bol.originals.courierReference && (
                    <> · courier <span className="mono">{bol.originals.courierReference}</span></>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>
      </div>

      {dialog === 'distribute' && (
        <DistributeModal busy={distribute.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Bill of lading sent',
            () => distribute.mutateAsync({ id: bol.id, ...input }))} />
      )}
      {dialog === 'release' && (
        <ReleaseModal busy={releaseOriginals.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Originals released',
            () => releaseOriginals.mutateAsync({ id: bol.id, ...input }))} />
      )}
      {dialog === 'surrender' && (
        <SurrenderModal outstanding={bol.originals?.outstanding ?? 0}
          busy={surrender.isPending} onClose={() => setDialog(null)}
          onSubmit={(count) => void run('Surrender recorded',
            () => surrender.mutateAsync({ id: bol.id, count }))} />
      )}
      {dialog === 'amend' && (
        <AmendModal busy={amend.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('New revision issued', async () => {
            const next = await amend.mutateAsync({ id: bol.id, ...input })
            navigate(`/documentation/${next.id}`)
            return next
          })} />
      )}
      {dialog === 'void' && (
        <VoidModal busy={voidBol.isPending} onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Bill of lading voided',
            () => voidBol.mutateAsync({ id: bol.id, reason }))} />
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

function DistributeModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    recipient: 'SHIPPER' | 'CONSIGNEE_AGENT' | 'NOTIFY_PARTY'
    recipientName: string | null
    channel: 'EMAIL' | 'PORTAL' | 'COURIER'
  }) => void
}) {
  const [recipient, setRecipient] = useState<'SHIPPER' | 'CONSIGNEE_AGENT' | 'NOTIFY_PARTY'>('SHIPPER')
  const [recipientName, setRecipientName] = useState('')
  const [channel, setChannel] = useState<'EMAIL' | 'PORTAL' | 'COURIER'>('EMAIL')

  return (
    <Modal title="Send bill of lading"
      description="Recorded against the current revision, so it stays answerable who received which version."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({ recipient, recipientName: recipientName || null, channel })
      }}>
        <div className="form-grid">
          <F label="Recipient">
            <select className="select" value={recipient}
              onChange={(e) => setRecipient(e.target.value as typeof recipient)}>
              <option value="SHIPPER">Shipper</option>
              <option value="CONSIGNEE_AGENT">Consignee agent</option>
              <option value="NOTIFY_PARTY">Notify party</option>
            </select></F>
          <F label="Recipient name">
            <input className="input" value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)} /></F>
          <F label="Channel">
            <select className="select" value={channel}
              onChange={(e) => setChannel(e.target.value as typeof channel)}>
              <option value="EMAIL">Email</option>
              <option value="PORTAL">Portal</option>
              <option value="COURIER">Courier</option>
            </select></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReleaseModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { releasedTo: string; courierReference: string | null }) => void
}) {
  const [releasedTo, setReleasedTo] = useState('')
  const [courierReference, setCourierReference] = useState('')
  return (
    <Modal title="Release originals"
      description="Three negotiable originals go out. Until all three come back, the bill cannot be amended."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({ releasedTo, courierReference: courierReference || null })
      }}>
        <F label="Released to">
          <input className="input" value={releasedTo}
            onChange={(e) => setReleasedTo(e.target.value)} required /></F>
        <F label="Courier reference">
          <input className="input mono" value={courierReference} placeholder="DHL-8891"
            onChange={(e) => setCourierReference(e.target.value)} /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Releasing…' : 'Release 3 originals'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function SurrenderModal({ outstanding, busy, onClose, onSubmit }: {
  outstanding: number
  busy: boolean
  onClose: () => void
  onSubmit: (count: number) => void
}) {
  const [count, setCount] = useState(outstanding)
  return (
    <Modal title="Record originals surrendered"
      description={`${outstanding} of the set are still outstanding.`} onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(count) }}>
        <F label="How many came back">
          <input type="number" min={1} max={outstanding} className="input numeric" value={count}
            onChange={(e) => setCount(Number(e.target.value))} /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record surrender'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function AmendModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    reason: string
    consigneeName?: string | null
    consigneeAddress?: string | null
    vesselName?: string | null
    sealNumber?: string | null
    weightKg?: number | null
  }) => void
}) {
  const [form, setForm] = useState({
    reason: '', consigneeName: '', consigneeAddress: '', vesselName: '', sealNumber: '', weightKg: '',
  })
  const set = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }))

  return (
    <Modal title="Amend bill of lading" width={620}
      description="A new revision, not a replacement. This one is voided and kept — it may be the copy someone is holding. Changing cargo figures also obliges an EEI amendment."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({
          reason: form.reason,
          consigneeName: form.consigneeName || null,
          consigneeAddress: form.consigneeAddress || null,
          vesselName: form.vesselName || null,
          sealNumber: form.sealNumber || null,
          weightKg: form.weightKg ? Number(form.weightKg) : null,
        })
      }}>
        <F label="Reason">
          <textarea className="textarea" value={form.reason}
            onChange={(e) => set('reason', e.target.value)}
            placeholder="Consignee address corrected" required /></F>
        <p className="faint" style={{ fontSize: 11.5 }}>
          Leave a field blank to carry it over unchanged.
        </p>
        <div className="form-grid">
          <F label="Consignee name">
            <input className="input" value={form.consigneeName}
              onChange={(e) => set('consigneeName', e.target.value)} /></F>
          <F label="Consignee address">
            <input className="input" value={form.consigneeAddress}
              onChange={(e) => set('consigneeAddress', e.target.value)} /></F>
          <F label="Vessel">
            <input className="input" value={form.vesselName}
              onChange={(e) => set('vesselName', e.target.value)} /></F>
          <F label="Seal number">
            <input className="input mono" value={form.sealNumber}
              onChange={(e) => set('sealNumber', e.target.value)} /></F>
          <F label="Weight (kg)" hint="Changing this triggers an EEI amendment">
            <input type="number" step="0.001" min={0} className="input numeric" value={form.weightKg}
              onChange={(e) => set('weightKg', e.target.value)} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Issuing…' : 'Issue new revision'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function VoidModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  return (
    <Modal title="Void bill of lading"
      description="Voids this revision without issuing a replacement. The record is kept."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(reason) }}>
        <F label="Reason">
          <textarea className="textarea" value={reason}
            onChange={(e) => setReason(e.target.value)} required /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Keep it</button>
          <button type="submit" className="btn btn-danger" disabled={busy}>
            {busy ? 'Voiding…' : 'Void'}
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
