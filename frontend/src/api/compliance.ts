import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { EEIFiling, FilingSystemStatus } from './types'

export interface CompileEEIDataInput {
  shipperName: string
  shipperEin: string
  shipperAddress: string | null
  consigneeName: string
  consigneeAddress: string | null
  consigneeCountry: string
  scheduleBNumber: string
  commodityDescription: string
  quantityValue: number
  quantityUnit: string
  valueUsd: number
  carrierScac: string
  vesselName: string | null
  voyageNumber: string | null
  portOfExportCode: string
  countryOfDestination: string
  estimatedEtd: string
  licenseRequired: boolean
}

export interface ExportLicenseInput {
  licenseNumber: string
  issuingAuthority: string
  licenseType: string
  commodityEccn: string | null
  validFrom: string
  validUntil: string
  valueAuthorized: number | null
}

const keys = {
  all: ['filings'] as const,
  detail: (id: string) => ['filings', id] as const,
  forBooking: (bookingId: string) => ['filings', 'booking', bookingId] as const,
  system: ['filing-system'] as const,
}

/** Whether filings are simulated. Cached indefinitely — it cannot change at runtime. */
export function useFilingSystem() {
  return useQuery({
    queryKey: keys.system,
    queryFn: () => api.get<FilingSystemStatus>('/api/compliance/filing-system'),
    staleTime: Infinity,
  })
}

export function useFilings() {
  return useQuery({
    queryKey: keys.all,
    queryFn: () => api.get<EEIFiling[]>('/api/compliance/filings'),
  })
}

export function useFiling(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => api.get<EEIFiling>(`/api/compliance/filings/${id}`),
    enabled: id !== '',
  })
}

export function useFilingsForBooking(bookingId: string) {
  return useQuery({
    queryKey: keys.forBooking(bookingId),
    queryFn: () => api.get<EEIFiling[]>(`/api/compliance/bookings/${bookingId}/filings`),
    enabled: bookingId !== '',
  })
}

function useFilingMutation<TInput>(id: string, send: (input: TInput) => Promise<EEIFiling>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: (filing) => {
      queryClient.setQueryData(keys.detail(id), filing)
      void queryClient.invalidateQueries({ queryKey: keys.all })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

/** Amendment and correction create a new filing, so the whole list is refetched. */
function useSpawningMutation(send: () => Promise<EEIFiling>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.all })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export function useInitiateFiling() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (bookingId: string) =>
      api.post<EEIFiling>(`/api/compliance/bookings/${bookingId}/filings`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.all })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
    },
  })
}

export const useCompileFiling = (id: string) =>
  useFilingMutation<CompileEEIDataInput>(id, (input) =>
    api.post<EEIFiling>(`/api/compliance/filings/${id}/compile`, input))

export const useSubmitFiling = (id: string) =>
  useFilingMutation<void>(id, () => api.post<EEIFiling>(`/api/compliance/filings/${id}/submit`))

export const useRecordAcceptance = (id: string) =>
  useFilingMutation<{ itnNumber: string; aesSubmissionReference: string | null }>(id, (input) =>
    api.post<EEIFiling>(`/api/compliance/filings/${id}/acceptance`, input))

export const useRecordRejection = (id: string) =>
  useFilingMutation<{ rejectionCode: string; rejectionDescription: string }>(id, (input) =>
    api.post<EEIFiling>(`/api/compliance/filings/${id}/rejection`, input))

export const useCorrectFiling = (id: string) =>
  useSpawningMutation(() => api.post<EEIFiling>(`/api/compliance/filings/${id}/correct`))

export const useAmendFiling = (id: string) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { reason: string }) =>
      api.post<EEIFiling>(`/api/compliance/filings/${id}/amend`, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.all })
      void queryClient.invalidateQueries({ queryKey: keys.detail(id) })
    },
  })
}

export const useCancelFiling = (id: string) =>
  useFilingMutation<{ reason: string }>(id, (input) =>
    api.post<EEIFiling>(`/api/compliance/filings/${id}/cancel`, input))

export const useRecordExportLicense = (id: string) =>
  useFilingMutation<ExportLicenseInput>(id, (input) =>
    api.post<EEIFiling>(`/api/compliance/filings/${id}/export-license`, input))
