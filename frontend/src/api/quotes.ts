import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { ChargeUnit, Quote, QuoteLineType, QuoteShippingMode } from './types'

export interface CargoDetailInput {
  description: string
  hsCode: string
  pieces: number
  weightKg: number
  lengthCm: number
  widthCm: number
  heightCm: number
  hazmat: boolean
  temperatureControlled: boolean
  oversized: boolean
}

export interface CreateQuoteInput {
  customerId: string
  shippingMode: QuoteShippingMode
  originPortCode: string
  destinationPortCode: string
  incoterms: string
  requestedEtd: string | null
  specialHandling: string | null
  insuranceRequired: boolean
  currency: string
  shipperName: string
  shipperAddress: string
  shipperCountry: string
  consigneeName: string
  consigneeAddress: string
  consigneeCountry: string
  cargoDetails: CargoDetailInput[]
}

export interface QuoteLineInput {
  lineType: QuoteLineType
  description: string
  buyRate: number
  sellRate: number
  quantity: number
  unit: ChargeUnit
}

export interface BuildQuotationInput {
  lines: QuoteLineInput[]
  validityDays: number
  rateValidUntil: string | null
  spotRate: boolean
  notes: string | null
}

const keys = {
  all: ['quotes'] as const,
  detail: (id: string) => ['quotes', id] as const,
}

export function useQuotes() {
  return useQuery({ queryKey: keys.all, queryFn: () => api.get<Quote[]>('/api/quotes') })
}

export function useQuote(id: string) {
  return useQuery({ queryKey: keys.detail(id), queryFn: () => api.get<Quote>(`/api/quotes/${id}`) })
}

/** Refreshes both the detail and the list, since a transition changes both. */
function useQuoteMutation<TInput>(
  id: string,
  send: (input: TInput) => Promise<Quote>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: (quote) => {
      queryClient.setQueryData(keys.detail(id), quote)
      void queryClient.invalidateQueries({ queryKey: keys.all })
    },
  })
}

export function useCreateQuote() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateQuoteInput) => api.post<Quote>('/api/quotes', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export const useBuildQuotation = (id: string) =>
  useQuoteMutation<BuildQuotationInput>(id, (input) => api.post<Quote>(`/api/quotes/${id}/build`, input))

export const useSendQuote = (id: string) =>
  useQuoteMutation<void>(id, () => api.post<Quote>(`/api/quotes/${id}/send`))

export const useAcceptQuote = (id: string) =>
  useQuoteMutation<{ selectedCarrierId: string }>(id, (input) =>
    api.post<Quote>(`/api/quotes/${id}/accept`, input))

export const useDeclineQuote = (id: string) =>
  useQuoteMutation<{ reason: string }>(id, (input) =>
    api.post<Quote>(`/api/quotes/${id}/decline`, input))

export const useExpireQuote = (id: string) =>
  useQuoteMutation<void>(id, () => api.post<Quote>(`/api/quotes/${id}/expire`))
