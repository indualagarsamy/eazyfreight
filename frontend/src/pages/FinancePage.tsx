import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  useAssignResponsibility, useCreditHolds, useInvoices, useIssueStorageFeeInvoice,
  useLiftCreditHold, usePayables, useStorageFees,
} from '../api/finance'
import type { StorageFeeResponsibility } from '../api/types'
import { PageHeader } from '../components/PageHeader'
import { StatusPill } from '../components/StatusPill'
import { GatedButton } from '../components/GatedButton'
import { Modal } from '../components/Modal'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { date, money, titleCase } from '../components/format'
import styles from './List.module.css'
import detail from './Detail.module.css'

type Tab = 'RECEIVABLES' | 'PAYABLES' | 'STORAGE' | 'HOLDS'

export function FinancePage() {
  const navigate = useNavigate()
  const { data: invoices, isPending, error, refetch } = useInvoices()
  const { data: payables } = usePayables()
  const { data: storageFees } = useStorageFees()
  const { data: creditHolds } = useCreditHolds()
  const [tab, setTab] = useState<Tab>('RECEIVABLES')
  const [assigning, setAssigning] = useState<string | null>(null)
  const toast = useToast()
  const assignResponsibility = useAssignResponsibility()
  const issueStorageInvoice = useIssueStorageFeeInvoice()
  const liftHold = useLiftCreditHold()

  const run = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      toast.success(label)
      setAssigning(null)
    } catch (err) {
      toast.fromError(err)
    }
  }

  /** Money in, money out, and what we keep — answerable per file, so summable. */
  const totals = useMemo(() => {
    const live = (invoices ?? []).filter((i) => i.status !== 'VOIDED')
    const sum = (pick: (i: (typeof live)[number]) => number) =>
      live.reduce((total, invoice) => total + pick(invoice), 0)
    return {
      billed: sum((i) => i.totalAmount),
      cost: sum((i) => i.totalBuyAmount),
      margin: sum((i) => i.margin),
      outstanding: sum((i) => i.outstandingAmount),
      overdue: live.filter((i) => i.overdue).length,
      payablesDue: (payables ?? []).filter((p) => p.status !== 'PAID').length,
      payablesOverdue: (payables ?? []).filter((p) => p.overdue).length,
    }
  }, [invoices, payables])

  const TABS: Array<{ key: Tab; label: string; count: number }> = [
    { key: 'RECEIVABLES', label: 'Receivables', count: invoices?.length ?? 0 },
    { key: 'PAYABLES', label: 'Carrier payables', count: payables?.length ?? 0 },
    { key: 'STORAGE', label: 'Storage fees', count: storageFees?.length ?? 0 },
    { key: 'HOLDS', label: 'Credit holds', count: creditHolds?.length ?? 0 },
  ]

  return (
    <>
      <PageHeader
        title="Finance"
        subtitle="Invoices, the carrier payables they fund, and margin per file"
      />

      <div className={styles.summary}>
        <Stat label="Billed" value={money(totals.billed)} />
        <Stat label="Carrier cost" value={money(totals.cost)} muted />
        <Stat label="Margin" value={money(totals.margin)} positive />
        <Stat label="Outstanding" value={money(totals.outstanding)}
          note={totals.overdue > 0 ? `${totals.overdue} overdue` : undefined} />
        <Stat label="Payables open" value={String(totals.payablesDue)}
          note={totals.payablesOverdue > 0 ? `${totals.payablesOverdue} past T+2` : undefined} />
      </div>

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="card">
          <div className={styles.toolbar}>
            <div className={styles.filters} role="tablist" aria-label="Finance views">
              {TABS.map((option) => (
                <button
                  key={option.key}
                  role="tab"
                  aria-selected={tab === option.key}
                  className={`${styles.filter} ${tab === option.key ? styles.filterActive : ''}`}
                  onClick={() => setTab(option.key)}
                >
                  {option.label}
                  <span className={styles.count}>{option.count}</span>
                </button>
              ))}
            </div>
          </div>

          {isPending ? (
            <Skeleton rows={5} />
          ) : tab === 'RECEIVABLES' ? (
            (invoices ?? []).length === 0 ? (
              <EmptyState title="No invoices yet"
                detail="An invoice is prepared automatically when a carrier confirms a booking." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Invoice</th><th>Type</th><th>Status</th>
                      <th className="right">Billed</th><th className="right">Cost</th>
                      <th className="right">Margin</th><th className="right">Outstanding</th>
                      <th>Due</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...(invoices ?? [])]
                      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                      .map((invoice) => (
                        <tr key={invoice.id} className="clickable"
                          onClick={() => navigate(`/finance/${invoice.id}`)}>
                          <td className="mono">{invoice.invoiceNumber}</td>
                          <td><StatusPill status={invoice.invoiceType} size="sm" /></td>
                          <td><StatusPill status={invoice.status} size="sm" /></td>
                          <td className="right numeric">{money(invoice.totalAmount, invoice.currency)}</td>
                          <td className="right numeric muted">
                            {money(invoice.totalBuyAmount, invoice.currency)}</td>
                          <td className="right numeric">
                            <span className={detail.margin}>
                              {money(invoice.margin, invoice.currency)}</span></td>
                          <td className="right numeric">
                            {money(invoice.outstandingAmount, invoice.currency)}</td>
                          <td className={invoice.overdue ? styles.urgent : 'muted'}>
                            {date(invoice.paymentDueDate)}
                            {invoice.overdue && (
                              <span className={styles.urgentNote}> · {invoice.daysOverdue}d late</span>
                            )}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            )
          ) : tab === 'PAYABLES' ? (
            (payables ?? []).length === 0 ? (
              <EmptyState title="No carrier payables"
                detail="A payable comes into being when a customer pays — we remit their money, not ours." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Status</th><th className="right">Amount</th>
                      <th>Customer paid</th><th>Due (T+2)</th>
                      <th>Carrier invoice</th><th className="right">Variance</th><th>Paid</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(payables ?? []).map((payable) => (
                      <tr key={payable.id} className="clickable"
                        onClick={() => navigate(`/finance/${payable.invoiceId}`)}>
                        <td><StatusPill status={payable.status} size="sm" /></td>
                        <td className="right numeric">{money(payable.amount, payable.currency)}</td>
                        <td className="muted">{date(payable.customerPaymentDate)}</td>
                        <td className={payable.overdue ? styles.urgent : undefined}>
                          {date(payable.dueDate)}</td>
                        <td className="mono muted">{payable.carrierInvoiceReference ?? '—'}</td>
                        <td className="right numeric">
                          {payable.carrierInvoiceAmount === null ? '—'
                            : money(payable.invoiceVariance, payable.currency)}</td>
                        <td className="muted">{date(payable.paidOn)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : tab === 'STORAGE' ? (
            (storageFees ?? []).length === 0 ? (
              <EmptyState title="No storage fees"
                detail="Raised when a container reaches the terminal before its earliest acceptance date." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Cause</th><th>Who pays</th><th className="right">Days</th>
                      <th className="right">Rate</th><th className="right">Amount</th>
                      <th>Invoiced</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {(storageFees ?? []).map((fee) => (
                      <tr key={fee.id}>
                        <td>{titleCase(fee.cause)}</td>
                        <td><StatusPill status={fee.responsibility} size="sm" /></td>
                        <td className="right numeric">{fee.days}</td>
                        <td className="right numeric muted">{money(fee.dailyRate, fee.currency)}</td>
                        <td className="right numeric">{money(fee.amount, fee.currency)}</td>
                        <td className="muted">
                          {fee.invoiceId ? 'Yes' : (fee.invoiceBlockedReason ?? 'No')}</td>
                        <td className="right">
                          <span className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                            <button className="btn btn-sm" onClick={() => setAssigning(fee.id)}>
                              {fee.responsibility === 'UNDETERMINED' ? 'Assign' : 'Reassign'}
                            </button>
                            <GatedButton size="sm" label="Invoice customer"
                              reason={fee.invoiceId ? 'Already invoiced' : fee.invoiceBlockedReason}
                              onClick={() => void run('Storage fee invoiced',
                                () => issueStorageInvoice.mutateAsync(fee.id))} />
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : (
            (creditHolds ?? []).length === 0 ? (
              <EmptyState title="No customers on credit hold"
                detail="A hold stops a customer taking new bookings while an invoice is unpaid." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr><th>Customer</th><th>Reason</th><th>Placed</th><th>By</th><th /></tr>
                  </thead>
                  <tbody>
                    {(creditHolds ?? []).map((hold) => (
                      <tr key={hold.id}>
                        <td className="mono">{hold.customerId.slice(0, 8)}</td>
                        <td>{hold.reason}</td>
                        <td className="muted">{date(hold.placedAt.slice(0, 10))}</td>
                        <td className="muted">{hold.placedBy}</td>
                        <td className="right">
                          {hold.active ? (
                            <button className="btn btn-sm"
                              onClick={() => void run('Credit hold lifted',
                                () => liftHold.mutateAsync(hold.customerId))}>Lift</button>
                          ) : (
                            <span className="faint">Lifted {date(hold.liftedAt?.slice(0, 10))}</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}
        </div>
      )}

      {assigning !== null && (
        <ResponsibilityModal busy={assignResponsibility.isPending}
          onClose={() => setAssigning(null)}
          onSubmit={(input) => void run('Responsibility assigned',
            () => assignResponsibility.mutateAsync({ id: assigning, ...input }))} />
      )}
    </>
  )
}

function ResponsibilityModal({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (input: { responsibility: StorageFeeResponsibility; notes: string | null }) => void
}) {
  const [responsibility, setResponsibility] = useState<StorageFeeResponsibility>('CUSTOMER')
  const [notes, setNotes] = useState('')
  return (
    <Modal title="Who pays the storage fee?"
      description="Only a fee the customer is responsible for can be invoiced. Anything we caused stays on our own margin, where it is visible."
      onClose={onClose}>
      <form className="stack" onSubmit={(e) => {
        e.preventDefault(); onSubmit({ responsibility, notes: notes || null })
      }}>
        <div className="field">
          <label htmlFor="responsibility">Responsibility</label>
          <select id="responsibility" className="select" value={responsibility}
            onChange={(e) => setResponsibility(e.target.value as StorageFeeResponsibility)}>
            <option value="CUSTOMER">Customer — delivered early against our advice</option>
            <option value="EAZY_FREIGHT">Eazy Freight — our instructions were late</option>
            <option value="CARRIER_DISPUTED">Carrier disputed — under discussion</option>
            <option value="UNDETERMINED">Undetermined — still investigating</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="notes">Notes</label>
          <textarea id="notes" className="textarea" value={notes}
            onChange={(e) => setNotes(e.target.value)} />
        </div>
        <div className="form-actions">
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Assign'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function Stat({ label, value, note, muted, positive }: {
  label: string; value: string; note?: string; muted?: boolean; positive?: boolean
}) {
  return (
    <div className={styles.stat}>
      <div className={styles.statLabel}>{label}</div>
      <div className={`${styles.statValue} numeric ${positive ? detail.margin : ''}`}
        style={muted ? { color: 'var(--text-muted)' } : undefined}>
        {value}
      </div>
      {note && <div className={styles.statNote}>{note}</div>}
    </div>
  )
}
