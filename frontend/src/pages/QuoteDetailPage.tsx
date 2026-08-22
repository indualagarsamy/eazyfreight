import { useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  useAcceptQuote, useBuildQuotation, useDeclineQuote,
  useExpireQuote, useQuote, useSendQuote,
} from '../api/quotes'
import type { BuildQuotationInput, QuoteLineInput } from '../api/quotes'
import type { ChargeUnit, Quote, QuoteLineType } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Lifecycle } from '../components/Lifecycle'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { date, dateTime, money, number, titleCase } from '../components/format'
import styles from './Detail.module.css'

const DEMO_CARRIER_ID = '1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d'

const LIFECYCLE = [
  { status: 'DRAFT' }, { status: 'SENT' }, { status: 'ACCEPTED' },
]

/**
 * Why an action is unavailable, phrased the way the domain would put it.
 *
 * This mirrors the aggregate's guards so the UI can disable and explain rather than
 * inviting a call that will be refused. The server remains the authority — anything
 * that slips through still comes back as a 409 and is shown as a toast.
 */
function unavailableReason(quote: Quote, action: 'build' | 'send' | 'accept' | 'decline' | 'expire') {
  switch (action) {
    case 'build':
      return quote.status === 'DRAFT' ? null : 'Only a draft quote can be priced'
    case 'send':
      if (quote.status !== 'DRAFT') return 'Only a draft quote can be sent'
      if (quote.screeningStatus !== 'CLEARED') {
        return quote.screeningStatus === 'FLAGGED'
          ? 'Denied party screening flagged this quote'
          : 'Denied party screening has not cleared yet'
      }
      if (quote.quoteLines.length === 0) return 'Price the quotation first'
      return null
    case 'accept':
      return quote.status === 'SENT' ? null : 'Only a sent quote can be accepted'
    case 'decline':
      return quote.status === 'SENT' ? null : 'Only a sent quote can be declined'
    case 'expire':
      return quote.status === 'DRAFT' || quote.status === 'SENT' ? null : 'This quote is already closed'
  }
}

export function QuoteDetailPage() {
  const { id = '' } = useParams()
  const toast = useToast()
  const { data: quote, isPending, error, refetch } = useQuote(id)

  const build = useBuildQuotation(id)
  const send = useSendQuote(id)
  const accept = useAcceptQuote(id)
  const decline = useDeclineQuote(id)
  const expire = useExpireQuote(id)

  const [showBuild, setShowBuild] = useState(false)
  const [showDecline, setShowDecline] = useState(false)

  if (isPending) return <div className="card"><Skeleton rows={6} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!quote) return null

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      toast.success(label)
    } catch (err) {
      toast.fromError(err)
    }
  }

  const action = (
    key: Parameters<typeof unavailableReason>[1],
    label: string,
    onClick: () => void,
    variant = '',
    size: 'sm' | 'md' = 'md',
  ) => (
    <GatedButton
      label={label}
      reason={unavailableReason(quote, key)}
      onClick={onClick}
      variant={variant}
      size={size}
    />
  )

  return (
    <>
      <PageHeader
        backTo={{ to: '/quotes', label: 'Quotes' }}
        title={<span className="mono">{quote.quoteReference}</span>}
        subtitle={`${quote.originPortCode} → ${quote.destinationPortCode} · ${titleCase(quote.shippingMode)} · ${quote.incoterms}`}
        badges={
          <>
            <StatusPill status={quote.status} />
            <StatusPill status={quote.screeningStatus}
              label={`Screening ${titleCase(quote.screeningStatus)}`} />
            {quote.spotRate && <StatusPill status="SPOT" tone="warning" label="Spot rate" />}
          </>
        }
        actions={
          <>
            {action('build', quote.quoteLines.length ? 'Re-price' : 'Price quotation',
              () => setShowBuild(true))}
            {action('send', 'Send to customer',
              () => void run('Quotation sent', () => send.mutateAsync()), 'btn-primary')}
            {action('accept', 'Accept', () => void run('Quote accepted',
              () => accept.mutateAsync({ selectedCarrierId: DEMO_CARRIER_ID })))}
            {action('decline', 'Decline', () => setShowDecline(true), 'btn-danger')}
          </>
        }
      />

      {quote.screeningStatus === 'FLAGGED' && (
        <div className={styles.banner}>
          <strong>Halted by denied party screening.</strong> A match was found on
          reference <span className="mono">{quote.screeningReferenceId}</span>. This quote
          cannot be sent. Do not disclose the reason to the customer.
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <Lifecycle
              steps={LIFECYCLE}
              current={quote.status}
              terminal={{ status: quote.status }}
            />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Rate breakdown</h2>
              {action('expire', 'Expire', () => void run('Quote expired',
                () => expire.mutateAsync()), 'btn-ghost', 'sm')}
            </div>
            {quote.quoteLines.length === 0 ? (
              <div className="card-body">
                <p className="muted">Not priced yet. Add rate lines to build the quotation.</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Charge</th>
                      <th className="right">Buy</th>
                      <th className="right">Sell</th>
                      <th className="right">Qty</th>
                      <th className="right">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {quote.quoteLines.map((line) => (
                      <tr key={line.id}>
                        <td>
                          {line.description}
                          <span className="faint" style={{ marginLeft: 6, fontSize: 11.5 }}>
                            {titleCase(line.lineType)}
                          </span>
                        </td>
                        <td className="right numeric muted">{money(line.buyRate, line.currency)}</td>
                        <td className="right numeric">{money(line.sellRate, line.currency)}</td>
                        <td className="right numeric muted">
                          {number(line.quantity, 4)} {line.unit}
                        </td>
                        <td className="right numeric">{money(line.amount, line.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className={styles.totalRow}>
                      <td>Total</td>
                      <td className="right numeric muted">{money(quote.totalBuyRate, quote.currency)}</td>
                      <td />
                      <td />
                      <td className="right numeric"><strong>{money(quote.totalSellRate, quote.currency)}</strong></td>
                    </tr>
                    <tr className={styles.marginRow}>
                      <td colSpan={4}>
                        Margin
                        <span className="faint" style={{ marginLeft: 8, fontSize: 11.5 }}>
                          visible per file, not reconstructed at month end
                        </span>
                      </td>
                      <td className="right numeric">
                        <strong className={styles.margin}>{money(quote.margin, quote.currency)}</strong>
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-header"><h2>Validity</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Quote valid until" value={date(quote.validUntil)} />
                <Item label="Rate valid until" value={date(quote.rateValidUntil)}
                  hint="Acceptance is refused once this passes" />
                <Item label="Requested ETD" value={date(quote.requestedEtd)} />
                <Item label="Created" value={dateTime(quote.createdAt)} />
                <Item label="Sent" value={dateTime(quote.sentAt)} />
                <Item label="Accepted" value={dateTime(quote.acceptedAt)} />
              </dl>
              {quote.declineReason && (
                <p className={styles.declineReason}>
                  <strong>Declined:</strong> {quote.declineReason}
                </p>
              )}
              {quote.notes && <p className={styles.notes}>{quote.notes}</p>}
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
                  <th className="right">Volumetric</th>
                  <th className="right">Chargeable</th>
                  <th>Flags</th>
                </tr>
              </thead>
              <tbody>
                {quote.cargoDetails.map((cargo) => (
                  <tr key={cargo.id}>
                    <td>{cargo.description}</td>
                    <td className="mono">{cargo.hsCode}</td>
                    <td className="right numeric">{cargo.pieces}</td>
                    <td className="right numeric">{number(cargo.weightKg, 3)} kg</td>
                    <td className="right numeric muted">
                      {number(cargo.volumetricWeightCbm, 4)} CBM
                    </td>
                    <td className="right numeric">
                      <strong>{number(cargo.chargeableWeight, 4)}</strong>{' '}
                      <span className="faint">{cargo.chargeableUnit ?? ''}</span>
                    </td>
                    <td>
                      <div className="row" style={{ gap: 5 }}>
                        {cargo.hazmat && <StatusPill status="HAZMAT" tone="danger" size="sm" />}
                        {cargo.temperatureControlled && <StatusPill status="REEFER" tone="info" size="sm" />}
                        {cargo.oversized && <StatusPill status="OOG" tone="warning" size="sm" />}
                        {!cargo.hazmat && !cargo.temperatureControlled && !cargo.oversized && (
                          <span className="faint">—</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {showBuild && (
        <BuildQuotationModal
          currency={quote.currency}
          existing={quote.quoteLines.map((line) => ({
            lineType: line.lineType, description: line.description,
            buyRate: line.buyRate, sellRate: line.sellRate,
            quantity: line.quantity, unit: line.unit,
          }))}
          busy={build.isPending}
          onClose={() => setShowBuild(false)}
          onSubmit={async (input) => {
            try {
              await build.mutateAsync(input)
              toast.success('Quotation priced')
              setShowBuild(false)
            } catch (err) {
              toast.fromError(err)
            }
          }}
        />
      )}

      {showDecline && (
        <DeclineModal
          busy={decline.isPending}
          onClose={() => setShowDecline(false)}
          onSubmit={async (reason) => {
            try {
              await decline.mutateAsync({ reason })
              toast.success('Quote declined')
              setShowDecline(false)
            } catch (err) {
              toast.fromError(err)
            }
          }}
        />
      )}
    </>
  )
}

function Item({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>
        {value}
        {hint && <div className="faint" style={{ fontSize: 11.5, marginTop: 2 }}>{hint}</div>}
      </dd>
    </div>
  )
}

const LINE_TYPES: QuoteLineType[] = [
  'BASE_FREIGHT', 'BAF', 'CAF', 'PSS', 'THC_ORIGIN', 'THC_DESTINATION', 'DOC_FEE', 'INSURANCE',
]
const UNITS: ChargeUnit[] = ['CBM', 'KG', 'TEU', 'FLAT']

function BuildQuotationModal({ currency, existing, busy, onClose, onSubmit }: {
  currency: string
  existing: QuoteLineInput[]
  busy: boolean
  onClose: () => void
  onSubmit: (input: BuildQuotationInput) => void
}) {
  const [lines, setLines] = useState<QuoteLineInput[]>(
    existing.length > 0 ? existing : [{
      lineType: 'BASE_FREIGHT', description: 'Ocean Freight',
      buyRate: 0, sellRate: 0, quantity: 1, unit: 'CBM',
    }],
  )
  const [validityDays, setValidityDays] = useState(30)
  const [rateValidUntil, setRateValidUntil] = useState('')
  const [spotRate, setSpotRate] = useState(false)
  const [notes, setNotes] = useState('')

  const setLine = <K extends keyof QuoteLineInput>(index: number, key: K, value: QuoteLineInput[K]) =>
    setLines((current) => current.map((line, i) => (i === index ? { ...line, [key]: value } : line)))

  const totalBuy = lines.reduce((sum, line) => sum + line.buyRate * line.quantity, 0)
  const totalSell = lines.reduce((sum, line) => sum + line.sellRate * line.quantity, 0)

  return (
    <Modal
      title="Price quotation"
      description="Buy and sell are recorded per line, so margin is a property of the quote."
      width={720}
      onClose={onClose}
    >
      <form
        className="stack"
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit({ lines, validityDays, rateValidUntil: rateValidUntil || null, spotRate, notes: notes || null })
        }}
      >
        <div className="stack" style={{ gap: 10 }}>
          {lines.map((line, index) => (
            <div key={index} className={styles.lineRow}>
              <select className="select" value={line.lineType}
                onChange={(e) => setLine(index, 'lineType', e.target.value as QuoteLineType)}>
                {LINE_TYPES.map((type) => <option key={type} value={type}>{titleCase(type)}</option>)}
              </select>
              <input className="input" placeholder="Description" value={line.description}
                onChange={(e) => setLine(index, 'description', e.target.value)} required />
              <input type="number" step="0.01" min={0} className="input numeric" placeholder="Buy"
                value={line.buyRate} onChange={(e) => setLine(index, 'buyRate', Number(e.target.value))} />
              <input type="number" step="0.01" min={0} className="input numeric" placeholder="Sell"
                value={line.sellRate} onChange={(e) => setLine(index, 'sellRate', Number(e.target.value))} />
              <input type="number" step="0.0001" min={0} className="input numeric" placeholder="Qty"
                value={line.quantity} onChange={(e) => setLine(index, 'quantity', Number(e.target.value))} />
              <select className="select" value={line.unit}
                onChange={(e) => setLine(index, 'unit', e.target.value as ChargeUnit)}>
                {UNITS.map((unit) => <option key={unit} value={unit}>{unit}</option>)}
              </select>
              <button type="button" className="btn btn-sm btn-ghost btn-danger"
                disabled={lines.length === 1}
                onClick={() => setLines((c) => c.filter((_, i) => i !== index))}>
                &times;
              </button>
            </div>
          ))}
          <div>
            <button type="button" className="btn btn-sm" onClick={() => setLines((c) => [...c, {
              lineType: 'DOC_FEE', description: '', buyRate: 0, sellRate: 0, quantity: 1, unit: 'FLAT',
            }])}>
              Add line
            </button>
          </div>
        </div>

        <div className={styles.previewTotals}>
          <span>Buy <strong className="numeric">{money(totalBuy, currency)}</strong></span>
          <span>Sell <strong className="numeric">{money(totalSell, currency)}</strong></span>
          <span className={styles.margin}>
            Margin <strong className="numeric">{money(totalSell - totalBuy, currency)}</strong>
          </span>
        </div>

        <div className="form-grid">
          <div className="field">
            <label>Validity (days)</label>
            <input type="number" min={1} className="input numeric" value={validityDays}
              onChange={(e) => setValidityDays(Number(e.target.value))} />
          </div>
          <div className="field">
            <label>Rate valid until</label>
            <input type="date" className="input" value={rateValidUntil}
              onChange={(e) => setRateValidUntil(e.target.value)} />
            <span className="hint">Blocks acceptance once passed</span>
          </div>
        </div>

        <label className="checkbox">
          <input type="checkbox" checked={spotRate} onChange={(e) => setSpotRate(e.target.checked)} />
          Spot rate
        </label>

        <div className="field">
          <label>Notes</label>
          <textarea className="textarea" value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="Subject to space and equipment availability." />
        </div>

        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save pricing'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function DeclineModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  return (
    <Modal title="Decline quote" description="Recording why keeps the loss reportable." onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(reason) }}>
        <div className="field">
          <label>Reason</label>
          <textarea className="textarea" value={reason} onChange={(e) => setReason(e.target.value)}
            placeholder="Customer found a lower rate with a competitor" required />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-danger" disabled={busy}>
            {busy ? 'Saving…' : 'Decline quote'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
