import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  useApplyActuals, useApprovePayable, useCreditHolds, useDownloadInvoicePdf, useInvoice,
  useIssueCreditNote, useIssueInvoice, usePayablesForBooking, usePlaceCreditHold,
  useRecordCarrierInvoice, useRecordCarrierPaid, useRecordPayment, useSendInvoicePdf,
  useVoidInvoice,
} from '../api/finance'
import type { InvoiceView, PaymentMethod } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { Lifecycle } from '../components/Lifecycle'
import { Timeline } from '../components/Timeline'
import type { TimelineEntry } from '../components/Timeline'
import { Modal } from '../components/Modal'
import { GatedButton } from '../components/GatedButton'
import { ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { date, dateTime, money, number, titleCase } from '../components/format'
import styles from './Detail.module.css'

const LIFECYCLE = [
  { status: 'PREPARED', label: 'Prepared' },
  { status: 'ISSUED', label: 'Issued' },
  { status: 'PAID', label: 'Paid' },
]

const TERMS_NOTE: Record<string, string> = {
  TWO_WEEKS_BEFORE_ARRIVAL:
    'Due 14 days before the vessel arrives, so the customer pays before the cargo can be released.',
  NET_30: 'Due 30 days from the invoice date. For established credit customers.',
}

type ActionKey =
  'actuals' | 'issue' | 'send' | 'payment' | 'void' | 'creditNote' | 'creditHold'

function unavailableReason(
        invoice: InvoiceView, action: ActionKey, onHold: boolean): string | null {
  switch (action) {
    case 'actuals':
      return invoice.status === 'PREPARED' ? null : 'Lines are frozen once the invoice is issued'
    case 'issue':
      return invoice.issueBlockedReason
    case 'send':
      return invoice.status === 'PREPARED' ? 'Issue the invoice first' : null
    case 'payment':
      if (invoice.status === 'PAID') return 'The invoice is settled'
      if (invoice.status === 'VOIDED') return 'The invoice is voided'
      return invoice.status === 'PREPARED' ? 'Issue the invoice first' : null
    case 'void':
      if (invoice.status === 'VOIDED') return 'Already voided'
      return invoice.paidAmount > 0
        ? 'Payments have been received — raise a credit note instead' : null
    case 'creditNote':
      return invoice.status === 'PREPARED' || invoice.status === 'VOIDED'
        ? 'A credit note can only be raised against an issued invoice' : null
    case 'creditHold':
      if (onHold) return 'This customer is already on credit hold'
      return invoice.overdue
        ? null
        : 'A hold is for an invoice that has gone past due — this one has not'
  }
}

export function FinanceDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data: invoice, isPending, error, refetch } = useInvoice(id)
  const { data: bookingPayables } = usePayablesForBooking(invoice?.bookingId ?? '')
  const { data: creditHolds } = useCreditHolds()

  const applyActuals = useApplyActuals()
  const issue = useIssueInvoice()
  const sendPdf = useSendInvoicePdf()
  const downloadPdf = useDownloadInvoicePdf()
  const recordPayment = useRecordPayment()
  const voidInvoice = useVoidInvoice()
  const creditNote = useIssueCreditNote()
  const carrierInvoice = useRecordCarrierInvoice()
  const approve = useApprovePayable()
  const carrierPaid = useRecordCarrierPaid()
  const placeHold = usePlaceCreditHold()

  const [dialog, setDialog] = useState<
    null | 'actuals' | 'payment' | 'void' | 'creditNote'
    | 'creditHold'
    | { kind: 'carrierInvoice' | 'carrierPaid'; payableId: string }>(null)

  if (isPending) return <div className="card"><Skeleton rows={7} /></div>
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!invoice) return null

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      toast.success(label)
      setDialog(null)
    } catch (err) {
      toast.fromError(err)
    }
  }

  /**
   * A booking can carry several invoices — freight, then a storage fee, then a credit
   * note — and each payment funds its own payable. Showing the booking's whole set
   * here would put another invoice's remittance on this page as though it belonged
   * to it, so only the payables this invoice funded are listed.
   */
  const payables = (bookingPayables ?? []).filter((p) => p.invoiceId === invoice.id)

  const onHold = (creditHolds ?? [])
    .some((hold) => hold.active && hold.customerId === invoice.customerId)

  const Action = ({ action, label, onClick, variant = '' }: {
    action: ActionKey; label: string; onClick: () => void; variant?: string
  }) => (
    <GatedButton label={label} reason={unavailableReason(invoice, action, onHold)}
      onClick={onClick} variant={variant} size="sm" />
  )

  const paymentEntries: TimelineEntry[] = invoice.payments.map((payment) => ({
    key: payment.id,
    tone: 'accent',
    title: <>{money(payment.amount, payment.currency)} · {titleCase(payment.paymentMethod)}</>,
    meta: `${date(payment.paymentDate)} · recorded by ${payment.recordedBy}`,
    body: payment.reference,
  }))

  return (
    <>
      <PageHeader
        backTo={{ to: '/finance', label: 'Finance' }}
        title={<span className="mono">{invoice.invoiceNumber}</span>}
        subtitle={
          <>
            {titleCase(invoice.invoiceType)} · booking{' '}
            <Link to={`/bookings/${invoice.bookingId}`} className="mono">
              {invoice.bookingId.slice(0, 8)}
            </Link>
          </>
        }
        badges={
          <>
            <StatusPill status={invoice.status} />
            {invoice.overdue && (
              <StatusPill status="OVERDUE" tone="danger"
                label={`${invoice.daysOverdue} days overdue`} />
            )}
          </>
        }
        actions={
          <>
            <Action action="issue" label="Issue to customer" variant="btn-primary"
              onClick={() => void run('Invoice issued', () => issue.mutateAsync(invoice.id))} />
            <Action action="payment" label="Record payment" onClick={() => setDialog('payment')} />
          </>
        }
      />

      {invoice.issueBlockedReason && invoice.status === 'PREPARED' && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          <strong>Not yet issuable.</strong> {invoice.issueBlockedReason}.
        </div>
      )}

      {onHold && (
        <div className={`${styles.banner} ${styles.bannerWarning}`}>
          <strong>This customer is on credit hold.</strong> New bookings are refused until
          the hold is lifted.
        </div>
      )}

      {invoice.status === 'VOIDED' && (
        <div className={styles.banner}>
          <strong>Voided {dateTime(invoice.voidedAt)}</strong> by {invoice.voidedBy} —{' '}
          {invoice.voidReason}
        </div>
      )}

      <div className="stack">
        <section className="card">
          <div className="card-body">
            <Lifecycle steps={LIFECYCLE} current={
              invoice.status === 'PARTIALLY_PAID' ? 'ISSUED' : invoice.status} />
          </div>
          <div className={styles.actionGroup}>
            <span className={styles.actionGroupLabel}>Invoice</span>
            <Action action="actuals" label="Set lines from actuals"
              onClick={() => setDialog('actuals')} />
            <button className="btn btn-sm" onClick={() => void run('Invoice downloaded',
              () => downloadPdf.mutateAsync(invoice.id))}>Download PDF</button>
            <Action action="send" label="Mark as sent"
              onClick={() => void run('Recorded as sent', () => sendPdf.mutateAsync(invoice.id))} />
            <Action action="creditNote" label="Raise credit note"
              onClick={() => setDialog('creditNote')} />
            <Action action="void" label="Void" variant="btn-danger"
              onClick={() => setDialog('void')} />
            <Action action="creditHold" label="Place customer on credit hold"
              onClick={() => setDialog('creditHold')} />
          </div>
        </section>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Charges</h2>
              <span className="faint" style={{ fontSize: 12 }}>Buy beside sell, so margin is visible</span>
            </div>
            {invoice.lines.length === 0 ? (
              <div className="card-body"><p className="muted">No lines yet.</p></div>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Charge</th><th className="right">Buy</th><th className="right">Sell</th>
                      <th className="right">Qty</th><th className="right">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoice.lines.map((line) => (
                      <tr key={line.id}>
                        <td>{line.description}</td>
                        <td className="right numeric muted">{money(line.buyAmount, invoice.currency)}</td>
                        <td className="right numeric">{money(line.sellAmount, invoice.currency)}</td>
                        <td className="right numeric muted">
                          {number(line.quantity, 4)} {line.unit ?? ''}</td>
                        <td className="right numeric">{money(line.extendedSell, invoice.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className={styles.totalRow}>
                      <td>Total</td>
                      <td className="right numeric muted">
                        {money(invoice.totalBuyAmount, invoice.currency)}</td>
                      <td /><td />
                      <td className="right numeric">
                        <strong>{money(invoice.totalAmount, invoice.currency)}</strong></td>
                    </tr>
                    <tr className={styles.marginRow}>
                      <td colSpan={4}>Margin on this file</td>
                      <td className="right numeric">
                        <strong className={styles.margin}>
                          {money(invoice.margin, invoice.currency)}</strong></td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-header"><h2>Terms and settlement</h2></div>
            <div className="card-body">
              <dl className="definition-list">
                <Item label="Terms" value={titleCase(invoice.paymentTermsType)} />
                <Item label="Invoice date" value={date(invoice.invoiceDate)} />
                <Item label="Due" value={date(invoice.paymentDueDate)} />
                <Item label="Vessel arrives" value={date(invoice.confirmedEta)} />
                <Item label="Paid" value={money(invoice.paidAmount, invoice.currency)} />
                <Item label="Outstanding"
                  value={money(invoice.outstandingAmount, invoice.currency)} />
              </dl>
              <p className="faint" style={{ marginTop: 12, fontSize: 11.5 }}>
                {TERMS_NOTE[invoice.paymentTermsType]}
              </p>
            </div>
          </section>
        </div>

        <div className={styles.split}>
          <section className="card">
            <div className="card-header">
              <h2>Payments received</h2>
              <span className="faint" style={{ fontSize: 12 }}>
                Each one funds a carrier payable
              </span>
            </div>
            <div className="card-body">
              {invoice.payments.length === 0 ? (
                <p className="muted">Nothing received yet.</p>
              ) : (
                <Timeline entries={paymentEntries} />
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <h2>Carrier payables</h2>
              <span className="faint" style={{ fontSize: 12 }}>Due two business days after we are paid</span>
            </div>
            <div className="card-body stack">
              {payables.length === 0 ? (
                <p className="muted">
                  None. A payable comes into being when the customer pays — we remit their
                  money, not our own, so there is nothing to owe until then.
                </p>
              ) : (
                payables.map((payable) => (
                  <div key={payable.id} className={styles.payable}>
                    <div>
                      <div className={styles.sailingLabel}>Customer paid</div>
                      <div className={styles.sailingValue}>{date(payable.customerPaymentDate)}</div>
                    </div>
                    <span className={styles.payableArrow} aria-hidden>&rarr;</span>
                    <div>
                      <div className={styles.sailingLabel}>Carrier due (T+2)</div>
                      <div className={styles.sailingValue}>{date(payable.dueDate)}</div>
                    </div>
                    <div className={styles.payableFoot}>
                      <StatusPill status={payable.status} size="sm" />
                      {payable.overdue && (
                        <StatusPill status="LATE" tone="danger" size="sm" label="Overdue" />
                      )}
                      <span className="numeric">{money(payable.amount, payable.currency)}</span>
                      <span className="spacer" />
                      {payable.status === 'PENDING' && (
                        <button className="btn btn-sm"
                          onClick={() => setDialog({ kind: 'carrierInvoice', payableId: payable.id })}>
                          Record carrier invoice
                        </button>
                      )}
                      {payable.status === 'AWAITING_INVOICE_MATCH' && (
                        <button className="btn btn-sm"
                          onClick={() => void run('Payment approved',
                            () => approve.mutateAsync(payable.id))}>
                          Approve
                        </button>
                      )}
                      {payable.status === 'APPROVED' && (
                        <button className="btn btn-sm btn-primary"
                          onClick={() => setDialog({ kind: 'carrierPaid', payableId: payable.id })}>
                          Record payment made
                        </button>
                      )}
                      {payable.status === 'PAID' && (
                        <span className="faint">Paid {date(payable.paidOn)}</span>
                      )}
                    </div>
                    {payable.carrierInvoiceReference && (
                      <div className={styles.payableFoot} style={{ borderTop: 'none' }}>
                        <span className="faint">
                          Carrier invoice{' '}
                          <span className="mono">{payable.carrierInvoiceReference}</span>
                          {payable.invoiceVariance !== 0 && (
                            <> &middot; variance {money(payable.invoiceVariance, payable.currency)}</>
                          )}
                        </span>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </div>

      {dialog === 'actuals' && (
        <LinesModal busy={applyActuals.isPending} invoice={invoice}
          onClose={() => setDialog(null)}
          onSubmit={(lines) => void run('Lines updated from actuals',
            () => applyActuals.mutateAsync({ id: invoice.id, lines }))} />
      )}
      {dialog === 'payment' && (
        <PaymentModal outstanding={invoice.outstandingAmount} busy={recordPayment.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Payment recorded — carrier payable created',
            () => recordPayment.mutateAsync({ id: invoice.id, ...input }))} />
      )}
      {dialog === 'creditHold' && (
        <ReasonModal title="Place customer on credit hold"
          label="Reason" confirmLabel="Place hold" busy={placeHold.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Customer placed on credit hold',
            () => placeHold.mutateAsync({
              bookingId: invoice.bookingId, customerId: invoice.customerId, reason,
            }))} />
      )}
      {dialog === 'void' && (
        <ReasonModal title="Void invoice" label="Reason" confirmLabel="Void" danger
          busy={voidInvoice.isPending} onClose={() => setDialog(null)}
          onSubmit={(reason) => void run('Invoice voided',
            () => voidInvoice.mutateAsync({ id: invoice.id, reason }))} />
      )}
      {dialog === 'creditNote' && (
        <CreditNoteModal max={invoice.totalAmount} busy={creditNote.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Credit note issued', async () => {
            const note = await creditNote.mutateAsync({ id: invoice.id, ...input })
            navigate(`/finance/${note.id}`)
            return note
          })} />
      )}
      {typeof dialog === 'object' && dialog?.kind === 'carrierInvoice' && (
        <CarrierInvoiceModal busy={carrierInvoice.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Carrier invoice recorded',
            () => carrierInvoice.mutateAsync({ id: dialog.payableId, ...input }))} />
      )}
      {typeof dialog === 'object' && dialog?.kind === 'carrierPaid' && (
        <CarrierPaidModal busy={carrierPaid.isPending} onClose={() => setDialog(null)}
          onSubmit={(input) => void run('Carrier payment recorded',
            () => carrierPaid.mutateAsync({ id: dialog.payableId, ...input }))} />
      )}
    </>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

function LinesModal({ invoice, busy, onClose, onSubmit }: {
  invoice: InvoiceView
  busy: boolean
  onClose: () => void
  onSubmit: (lines: Array<{
    description: string; buyAmount: number; sellAmount: number
    quantity: number; unit: string | null
  }>) => void
}) {
  const [lines, setLines] = useState(
    invoice.lines.length > 0
      ? invoice.lines.map((l) => ({
          description: l.description, buyAmount: l.buyAmount,
          sellAmount: l.sellAmount, quantity: l.quantity, unit: l.unit ?? 'FLAT',
        }))
      : [{ description: 'Ocean Freight', buyAmount: 0, sellAmount: 0, quantity: 1, unit: 'CBM' }])

  const set = (index: number, key: string, value: string | number) =>
    setLines((current) => current.map((line, i) =>
      i === index ? { ...line, [key]: value } : line))

  const totalBuy = lines.reduce((t, l) => t + l.buyAmount * l.quantity, 0)
  const totalSell = lines.reduce((t, l) => t + l.sellAmount * l.quantity, 0)

  return (
    <Modal title="Set invoice lines from actuals" width={720}
      description="These are the figures the House BOL carries. Buy and sell are both recorded, so margin is a property of the invoice."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(lines) }}>
        <div className="stack" style={{ gap: 10 }}>
          {lines.map((line, index) => (
            <div key={index} className={styles.lineRow}
              style={{ gridTemplateColumns: 'minmax(0,1fr) 100px 100px 84px 84px 32px' }}>
              <input className="input" value={line.description}
                onChange={(e) => set(index, 'description', e.target.value)} required />
              <input type="number" step="0.01" min={0} className="input numeric" placeholder="Buy"
                value={line.buyAmount} onChange={(e) => set(index, 'buyAmount', Number(e.target.value))} />
              <input type="number" step="0.01" min={0} className="input numeric" placeholder="Sell"
                value={line.sellAmount} onChange={(e) => set(index, 'sellAmount', Number(e.target.value))} />
              <input type="number" step="0.0001" min={0} className="input numeric" placeholder="Qty"
                value={line.quantity} onChange={(e) => set(index, 'quantity', Number(e.target.value))} />
              <input className="input mono" value={line.unit ?? ''}
                onChange={(e) => set(index, 'unit', e.target.value)} />
              <button type="button" className="btn btn-sm btn-ghost btn-danger"
                disabled={lines.length === 1}
                onClick={() => setLines((c) => c.filter((_, i) => i !== index))}>&times;</button>
            </div>
          ))}
          <div>
            <button type="button" className="btn btn-sm" onClick={() => setLines((c) => [...c,
              { description: '', buyAmount: 0, sellAmount: 0, quantity: 1, unit: 'FLAT' }])}>
              Add line
            </button>
          </div>
        </div>
        <div className={styles.previewTotals}>
          <span>Cost <strong className="numeric">{money(totalBuy, invoice.currency)}</strong></span>
          <span>Billed <strong className="numeric">{money(totalSell, invoice.currency)}</strong></span>
          <span className={styles.margin}>
            Margin <strong className="numeric">{money(totalSell - totalBuy, invoice.currency)}</strong>
          </span>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save lines'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function PaymentModal({ outstanding, busy, onClose, onSubmit }: {
  outstanding: number
  busy: boolean
  onClose: () => void
  onSubmit: (input: {
    amount: number; paymentDate: string
    paymentMethod: PaymentMethod; reference: string | null
  }) => void
}) {
  const [amount, setAmount] = useState(outstanding)
  const [paymentDate, setPaymentDate] = useState('')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('WIRE')
  const [reference, setReference] = useState('')

  return (
    <Modal title="Record customer payment"
      description="This creates the carrier payable it funds, due two business days later. The two are causally linked, not two separate calendars."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault()
        onSubmit({ amount, paymentDate, paymentMethod, reference: reference || null })
      }}>
        <div className="form-grid">
          <F label="Amount" hint={`Outstanding ${money(outstanding)}`}>
            <input type="number" step="0.01" min={0.01} max={outstanding}
              className="input numeric" value={amount}
              onChange={(e) => setAmount(Number(e.target.value))} required /></F>
          <F label="Payment date" hint="The T+2 window runs from this date">
            <input type="date" className="input" value={paymentDate}
              onChange={(e) => setPaymentDate(e.target.value)} required /></F>
          <F label="Method">
            <select className="select" value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}>
              <option value="WIRE">Wire</option>
              <option value="CHECK">Check</option>
              <option value="ACH">ACH</option>
            </select></F>
          <F label="Reference">
            <input className="input mono" value={reference}
              onChange={(e) => setReference(e.target.value)} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Recording…' : 'Record payment'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function CarrierInvoiceModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { reference: string; amount: number }) => void
}) {
  const [reference, setReference] = useState('')
  const [amount, setAmount] = useState(0)
  return (
    <Modal title="Record carrier invoice"
      description="Matching comes before approval, and approval before money moves."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit({ reference, amount }) }}>
        <div className="form-grid">
          <F label="Carrier invoice reference">
            <input className="input mono" value={reference}
              onChange={(e) => setReference(e.target.value)} required /></F>
          <F label="Amount billed" hint="Any difference from what we set aside shows as variance">
            <input type="number" step="0.01" min={0} className="input numeric" value={amount}
              onChange={(e) => setAmount(Number(e.target.value))} required /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function CarrierPaidModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { paidOn: string; reference: string | null }) => void
}) {
  const [paidOn, setPaidOn] = useState('')
  const [reference, setReference] = useState('')
  return (
    <Modal title="Record carrier payment" onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault(); onSubmit({ paidOn, reference: reference || null })
      }}>
        <div className="form-grid">
          <F label="Paid on">
            <input type="date" className="input" value={paidOn}
              onChange={(e) => setPaidOn(e.target.value)} required /></F>
          <F label="Reference">
            <input className="input mono" value={reference}
              onChange={(e) => setReference(e.target.value)} /></F>
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Record payment'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function CreditNoteModal({ max, busy, onClose, onSubmit }: {
  max: number
  busy: boolean
  onClose: () => void
  onSubmit: (input: { amount: number; reason: string }) => void
}) {
  const [amount, setAmount] = useState(0)
  const [reason, setReason] = useState('')
  return (
    <Modal title="Raise credit note"
      description="A separate negative document. The original invoice is never edited."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit({ amount, reason }) }}>
        <F label="Amount to credit" hint={`Invoice total ${money(max)}`}>
          <input type="number" step="0.01" min={0.01} className="input numeric" value={amount}
            onChange={(e) => setAmount(Number(e.target.value))} required /></F>
        <F label="Reason">
          <textarea className="textarea" value={reason}
            onChange={(e) => setReason(e.target.value)} required /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Issuing…' : 'Issue credit note'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReasonModal({ title, label, confirmLabel, danger, busy, onClose, onSubmit }: {
  title: string; label: string; confirmLabel: string; danger?: boolean
  busy: boolean; onClose: () => void; onSubmit: (value: string) => void
}) {
  const [value, setValue] = useState('')
  return (
    <Modal title={title} onClose={onClose}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(value) }}>
        <F label={label}>
          <textarea className="textarea" value={value}
            onChange={(e) => setValue(e.target.value)} required /></F>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`}
            disabled={busy}>{busy ? 'Saving…' : confirmLabel}</button>
        </div>
      </form>
    </Modal>
  )
}

function F({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}
