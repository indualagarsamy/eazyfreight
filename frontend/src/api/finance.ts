import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  CreditHoldView, InvoiceView, PayableView, PaymentMethod,
  StorageFeeCause, StorageFeeResponsibility, StorageFeeView,
} from './types'

export interface LineInput {
  description: string
  buyAmount: number
  sellAmount: number
  quantity: number
  unit: string | null
}

const keys = {
  root: ['finance'] as const,
  invoices: ['finance', 'invoices'] as const,
  invoice: (id: string) => ['finance', 'invoice', id] as const,
  forBooking: (b: string) => ['finance', 'booking', b] as const,
  payables: ['finance', 'payables'] as const,
  storageFees: ['finance', 'storage-fees'] as const,
  creditHolds: ['finance', 'credit-holds'] as const,
}

export function useInvoices() {
  return useQuery({
    queryKey: keys.invoices,
    queryFn: () => api.get<InvoiceView[]>('/api/finance/invoices'),
  })
}

export function useInvoice(id: string) {
  return useQuery({
    queryKey: keys.invoice(id),
    queryFn: () => api.get<InvoiceView>(`/api/finance/invoices/${id}`),
    enabled: id !== '',
  })
}

export function useInvoicesForBooking(bookingId: string) {
  return useQuery({
    queryKey: [...keys.forBooking(bookingId), 'invoices'],
    queryFn: () => api.get<InvoiceView[]>(`/api/finance/bookings/${bookingId}/invoices`),
    enabled: bookingId !== '',
  })
}

export function usePayablesForBooking(bookingId: string) {
  return useQuery({
    queryKey: [...keys.forBooking(bookingId), 'payables'],
    queryFn: () => api.get<PayableView[]>(`/api/finance/bookings/${bookingId}/payables`),
    enabled: bookingId !== '',
  })
}

export function usePayables() {
  return useQuery({
    queryKey: keys.payables,
    queryFn: () => api.get<PayableView[]>('/api/finance/payables'),
  })
}

export function useStorageFees() {
  return useQuery({
    queryKey: keys.storageFees,
    queryFn: () => api.get<StorageFeeView[]>('/api/finance/storage-fees'),
  })
}

export function useCreditHolds() {
  return useQuery({
    queryKey: keys.creditHolds,
    queryFn: () => api.get<CreditHoldView[]>('/api/finance/credit-holds'),
  })
}

/** Anything financial can move a payable or an invoice, so refetch the context. */
function useFinanceMutation<TInput, TResult>(send: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.root })
    },
  })
}

export const usePrepareInvoice = () =>
  useFinanceMutation<string, InvoiceView>((bookingId) =>
    api.post(`/api/finance/bookings/${bookingId}/invoice`, {}))

export const useApplyActuals = () =>
  useFinanceMutation<{ id: string; lines: LineInput[] }, InvoiceView>(({ id, lines }) =>
    api.post(`/api/finance/invoices/${id}/actuals`, { lines }))

export const useIssueInvoice = () =>
  useFinanceMutation<string, InvoiceView>((id) =>
    api.post(`/api/finance/invoices/${id}/issue`))

export const useSendInvoicePdf = () =>
  useFinanceMutation<string, InvoiceView>((id) =>
    api.post(`/api/finance/invoices/${id}/send`))

export const useRecordPayment = () =>
  useFinanceMutation<{
    id: string; amount: number; paymentDate: string
    paymentMethod: PaymentMethod; reference: string | null
  }, InvoiceView>(({ id, ...body }) =>
    api.post(`/api/finance/invoices/${id}/payments`, body))

export const useVoidInvoice = () =>
  useFinanceMutation<{ id: string; reason: string }, InvoiceView>(({ id, ...body }) =>
    api.post(`/api/finance/invoices/${id}/void`, body))

export const useIssueCreditNote = () =>
  useFinanceMutation<{ id: string; amount: number; reason: string }, InvoiceView>(
    ({ id, ...body }) => api.post(`/api/finance/invoices/${id}/credit-note`, body))

export const useRecordCarrierInvoice = () =>
  useFinanceMutation<{ id: string; reference: string; amount: number }, PayableView>(
    ({ id, ...body }) => api.post(`/api/finance/payables/${id}/carrier-invoice`, body))

export const useApprovePayable = () =>
  useFinanceMutation<string, PayableView>((id) =>
    api.post(`/api/finance/payables/${id}/approve`))

export const useRecordCarrierPaid = () =>
  useFinanceMutation<{ id: string; paidOn: string; reference: string | null }, PayableView>(
    ({ id, ...body }) => api.post(`/api/finance/payables/${id}/paid`, body))

export const useCalculateStorageFee = () =>
  useFinanceMutation<{
    bookingId: string; cause: StorageFeeCause; dailyRate: number; days: number
  }, StorageFeeView>(({ bookingId, ...body }) =>
    api.post(`/api/finance/bookings/${bookingId}/storage-fee`, body))

export const useAssignResponsibility = () =>
  useFinanceMutation<{
    id: string; responsibility: StorageFeeResponsibility; notes: string | null
  }, StorageFeeView>(({ id, ...body }) =>
    api.post(`/api/finance/storage-fees/${id}/responsibility`, body))

export const useIssueStorageFeeInvoice = () =>
  useFinanceMutation<string, InvoiceView>((id) =>
    api.post(`/api/finance/storage-fees/${id}/invoice`))

export const usePlaceCreditHold = () =>
  useFinanceMutation<{ bookingId: string; customerId: string; reason: string },
    CreditHoldView>(({ bookingId, ...body }) =>
      api.post(`/api/finance/bookings/${bookingId}/credit-hold`, body))

export const useLiftCreditHold = () =>
  useFinanceMutation<string, CreditHoldView>((customerId) =>
    api.post(`/api/finance/credit-holds/${customerId}/lift`))
