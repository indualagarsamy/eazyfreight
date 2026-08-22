import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { AwaitingDispatch, ExaminationResult, ItnGate, Logistics } from './types'

export interface DispatchInput {
  driverId: string | null
  truckingVendorId: string | null
  vehicleReference: string | null
  pickupAddress: string
  deliveryAddress: string
  scheduledPickupDate: string | null
  scheduledDeliveryDate: string | null
}

export interface TerminalReceiptInput {
  gateReceiptNumber: string
  terminalName: string
  earliestAcceptanceDate: string | null
  vesselCutOffDate: string | null
  storageFeeDailyRate: number | null
}

const keys = {
  all: ['logistics'] as const,
  detail: (bookingId: string) => ['logistics', bookingId] as const,
  gate: (bookingId: string) => ['logistics', bookingId, 'itn-gate'] as const,
  awaiting: ['logistics', 'awaiting-outbound-dispatch'] as const,
}

export function useAllLogistics() {
  return useQuery({ queryKey: keys.all, queryFn: () => api.get<Logistics[]>('/api/logistics') })
}

export function useLogistics(bookingId: string) {
  return useQuery({
    queryKey: keys.detail(bookingId),
    queryFn: () => api.get<Logistics>(`/api/logistics/bookings/${bookingId}`),
    enabled: bookingId !== '',
    // A 404 means the track has not started, which is a normal state, not an error.
    retry: false,
  })
}

/** Bookings that need a truck and have no outbound dispatch yet. */
export function useAwaitingOutboundDispatch() {
  return useQuery({
    queryKey: keys.awaiting,
    queryFn: () => api.get<AwaitingDispatch[]>('/api/logistics/awaiting-outbound-dispatch'),
  })
}

export function useItnGate(bookingId: string) {
  return useQuery({
    queryKey: keys.gate(bookingId),
    queryFn: () => api.get<ItnGate>(`/api/logistics/bookings/${bookingId}/itn-gate`),
    enabled: bookingId !== '',
    retry: false,
  })
}

function useLogisticsMutation<TInput>(
  bookingId: string,
  send: (input: TInput) => Promise<Logistics>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: (logistics) => {
      queryClient.setQueryData(keys.detail(bookingId), logistics)
      void queryClient.invalidateQueries({ queryKey: keys.all })
      void queryClient.invalidateQueries({ queryKey: keys.gate(bookingId) })
      void queryClient.invalidateQueries({ queryKey: keys.awaiting })
      // Preconditions for the Documentation track may have just been satisfied.
      void queryClient.invalidateQueries({ queryKey: ['documentation'] })
    },
  })
}

const post = (bookingId: string, path: string, body?: unknown) =>
  api.post<Logistics>(`/api/logistics/bookings/${bookingId}/${path}`, body)

export const useDispatchOutbound = (id: string) =>
  useLogisticsMutation<DispatchInput>(id, (input) => post(id, 'outbound-dispatch', input))

export const useDispatchInbound = (id: string) =>
  useLogisticsMutation<DispatchInput>(id, (input) => post(id, 'inbound-dispatch', input))

export const useRecordContainerNumber = (id: string) =>
  useLogisticsMutation<{ containerNumber: string; source: string }>(id, (input) =>
    post(id, 'container-number', input))

export const useRecordDeliveredToCustomer = (id: string) =>
  useLogisticsMutation<void>(id, () => post(id, 'delivered-to-customer'))

export const useRecordLoadingComplete = (id: string) =>
  useLogisticsMutation<void>(id, () => post(id, 'loading-complete'))

export const useRecordSeal = (id: string) =>
  useLogisticsMutation<{ sealNumber: string }>(id, (input) => post(id, 'seal', input))

export const useReplaceSeal = (id: string) =>
  useLogisticsMutation<{ sealNumber: string }>(id, (input) => post(id, 'seal/replace', input))

export const useRecordLoadedContainerPickedUp = (id: string) =>
  useLogisticsMutation<void>(id, () => post(id, 'loaded-container-picked-up'))

export const useRecordDeliveredToPort = (id: string) =>
  useLogisticsMutation<void>(id, () => post(id, 'delivered-to-port'))

export const useRecordTerminalReceipt = (id: string) =>
  useLogisticsMutation<TerminalReceiptInput>(id, (input) => post(id, 'terminal-receipt', input))

export const useRecordTerminalRejection = (id: string) =>
  useLogisticsMutation<{ reason: string }>(id, (input) => post(id, 'terminal-rejection', input))

export const useRecordExaminationHold = (id: string) =>
  useLogisticsMutation<{ cbpOfficerId: string | null; notes: string | null }>(id, (input) =>
    post(id, 'examination-hold', input))

export const useRecordExaminationRelease = (id: string) =>
  useLogisticsMutation<{
    result: ExaminationResult
    replacementSealNumber: string | null
    notes: string | null
  }>(id, (input) => post(id, 'examination-release', input))

export const useRecordLoadedOnVessel = (id: string) =>
  useLogisticsMutation<{ vesselName: string | null }>(id, (input) =>
    post(id, 'loaded-on-vessel', input))

export const useRecordVesselDeparted = (id: string) =>
  useLogisticsMutation<void>(id, () => post(id, 'vessel-departed'))

export const useRecordActualCargo = (id: string) =>
  useLogisticsMutation<{
    actualWeightKg: number; actualPieces: number; actualCbm: number | null
  }>(id, (input) => post(id, 'actual-cargo', input))
