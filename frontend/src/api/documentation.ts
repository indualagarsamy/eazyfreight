import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  DistributionChannel, DistributionRecipient, FreightTerms,
  HouseBOL, Instructions, MasterBOL, Preconditions, ReleaseType,
} from './types'

export interface CompileInstructionsInput {
  shipperName: string
  shipperAddress: string | null
  consigneeName: string
  consigneeAddress: string | null
  notifyPartyName: string | null
  notifyPartyAddress: string | null
  marksAndNumbers: string | null
  freightTerms: FreightTerms
  documentationCutOffDate: string | null
}

const keys = {
  root: ['documentation'] as const,
  activeHouse: ['documentation', 'house-bols'] as const,
  house: (id: string) => ['documentation', 'house-bol', id] as const,
  revisions: (n: string) => ['documentation', 'revisions', n] as const,
  forBooking: (b: string) => ['documentation', 'booking', b] as const,
  preconditions: (b: string) => ['documentation', 'preconditions', b] as const,
}

export function usePreconditions(bookingId: string) {
  return useQuery({
    queryKey: keys.preconditions(bookingId),
    queryFn: () => api.get<Preconditions>(`/api/documentation/bookings/${bookingId}/preconditions`),
    enabled: bookingId !== '',
  })
}

export function useActiveHouseBols() {
  return useQuery({
    queryKey: keys.activeHouse,
    queryFn: () => api.get<HouseBOL[]>('/api/documentation/house-bols'),
  })
}

export function useHouseBol(id: string) {
  return useQuery({
    queryKey: keys.house(id),
    queryFn: () => api.get<HouseBOL>(`/api/documentation/house-bols/${id}`),
    enabled: id !== '',
  })
}

export function useRevisions(houseBolNumber: string) {
  return useQuery({
    queryKey: keys.revisions(houseBolNumber),
    queryFn: () =>
      api.get<HouseBOL[]>(`/api/documentation/house-bols/by-number/${houseBolNumber}/revisions`),
    enabled: houseBolNumber !== '',
  })
}

export function useInstructionsForBooking(bookingId: string) {
  return useQuery({
    queryKey: [...keys.forBooking(bookingId), 'instructions'],
    queryFn: () => api.get<Instructions[]>(`/api/documentation/bookings/${bookingId}/instructions`),
    enabled: bookingId !== '',
  })
}

export function useMasterBolsForBooking(bookingId: string) {
  return useQuery({
    queryKey: [...keys.forBooking(bookingId), 'master-bols'],
    queryFn: () => api.get<MasterBOL[]>(`/api/documentation/bookings/${bookingId}/master-bols`),
    enabled: bookingId !== '',
  })
}

export function useHouseBolsForBooking(bookingId: string) {
  return useQuery({
    queryKey: [...keys.forBooking(bookingId), 'house-bols'],
    queryFn: () => api.get<HouseBOL[]>(`/api/documentation/bookings/${bookingId}/house-bols`),
    enabled: bookingId !== '',
  })
}

/** Everything in this context is interlinked, so any command refetches the lot. */
function useDocMutation<TInput, TResult>(send: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.root })
    },
  })
}

export const useCompileInstructions = (bookingId: string) =>
  useDocMutation<CompileInstructionsInput, Instructions>((input) =>
    api.post(`/api/documentation/bookings/${bookingId}/instructions`, input))

export const useApproveInstructions = () =>
  useDocMutation<string, Instructions>((id) =>
    api.post(`/api/documentation/instructions/${id}/approve`))

export const useSendInstructions = () =>
  useDocMutation<string, Instructions>((id) =>
    api.post(`/api/documentation/instructions/${id}/send`))

export const useRecordCarrierQuery = () =>
  useDocMutation<{ id: string; query: string }, Instructions>(({ id, query }) =>
    api.post(`/api/documentation/instructions/${id}/carrier-query`, { query }))

export const useResendCorrected = () =>
  useDocMutation<{ id: string; input: CompileInstructionsInput }, Instructions>(({ id, input }) =>
    api.post(`/api/documentation/instructions/${id}/resend-corrected`, input))

export const useRecordMasterBol = () =>
  useDocMutation<{ id: string; masterBolNumber: string; documentFileReference: string | null },
    MasterBOL>(({ id, ...body }) =>
      api.post(`/api/documentation/instructions/${id}/master-bol`, body))

export const useVerifyMasterBol = () =>
  useDocMutation<string, MasterBOL>((id) =>
    api.post(`/api/documentation/master-bols/${id}/verify`))

export const useRaiseDiscrepancy = () =>
  useDocMutation<{ id: string; discrepancyFields: string[] }, MasterBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/master-bols/${id}/discrepancy`, body))

export const useRecordMasterBolCorrection = () =>
  useDocMutation<{ id: string; correctedMasterBolNumber: string | null }, MasterBOL>(
    ({ id, ...body }) => api.post(`/api/documentation/master-bols/${id}/correction`, body))

export const useGenerateHouseBol = () =>
  useDocMutation<{ id: string; releaseType: ReleaseType }, HouseBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/master-bols/${id}/house-bol`, body))

/**
 * Renders the House BOL and saves it locally.
 *
 * <p>Still a mutation: the command records that the document was produced, which the
 * detail page shows and the distribution history refers to. The invalidation matters —
 * without it the page keeps saying the PDF has not been generated after it plainly has.
 */
export const useGeneratePdf = () =>
  useDocMutation<string, string>((id) =>
    api.download(`/api/documentation/house-bols/${id}/pdf`, 'POST'))

export const useDistribute = () =>
  useDocMutation<{
    id: string
    recipient: DistributionRecipient
    recipientName: string | null
    channel: DistributionChannel
  }, HouseBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/house-bols/${id}/distribute`, body))

export const useReleaseOriginals = () =>
  useDocMutation<{ id: string; releasedTo: string; courierReference: string | null }, HouseBOL>(
    ({ id, ...body }) => api.post(`/api/documentation/house-bols/${id}/originals/release`, body))

export const useSurrenderOriginals = () =>
  useDocMutation<{ id: string; count: number }, HouseBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/house-bols/${id}/originals/surrender`, body))

export const useAmendHouseBol = () =>
  useDocMutation<{
    id: string
    reason: string
    consigneeName?: string | null
    consigneeAddress?: string | null
    notifyPartyName?: string | null
    vesselName?: string | null
    sealNumber?: string | null
    cargoDescription?: string | null
    weightKg?: number | null
    pieces?: number | null
  }, HouseBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/house-bols/${id}/amend`, body))

export const useVoidHouseBol = () =>
  useDocMutation<{ id: string; reason: string }, HouseBOL>(({ id, ...body }) =>
    api.post(`/api/documentation/house-bols/${id}/void`, body))
