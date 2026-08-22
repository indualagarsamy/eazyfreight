import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  Booking, BookingShippingMode, BookingSourceType,
  CancellationInitiator, ContainerType,
} from './types'

export interface BookingCargoInput {
  description: string
  hsCode: string
  pieces: number
  weightKg: number
  valueUsd: number
  lengthCm: number
  widthCm: number
  heightCm: number
  hazmat: boolean
  temperatureControlled: boolean
  oversized: boolean
  marksAndNumbers: string | null
}

export interface CreateBookingInput {
  quoteId: string | null
  customerId: string
  shipperId: string
  consigneeId: string
  shippingMode: BookingShippingMode
  originPortCode: string
  destinationPortCode: string
  incoterms: string
  requestedEtd: string
  requestedEta: string | null
  transportRequired: boolean
  pickupAddress: string | null
  specialInstructions: string | null
  cargoDetails: BookingCargoInput[]
}

const keys = {
  all: ['bookings'] as const,
  detail: (id: string) => ['bookings', id] as const,
}

export function useBookings() {
  return useQuery({ queryKey: keys.all, queryFn: () => api.get<Booking[]>('/api/bookings') })
}

export function useBooking(id: string) {
  return useQuery({ queryKey: keys.detail(id), queryFn: () => api.get<Booking>(`/api/bookings/${id}`) })
}

function useBookingMutation<TInput>(id: string, send: (input: TInput) => Promise<Booking>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: (booking) => {
      queryClient.setQueryData(keys.detail(id), booking)
      void queryClient.invalidateQueries({ queryKey: keys.all })
    },
  })
}

export function useCreateBooking() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateBookingInput) => api.post<Booking>('/api/bookings', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all }),
  })
}

export const useSubmitBooking = (id: string) =>
  useBookingMutation<{
    bookingSourceType: BookingSourceType
    carrierId: string
    containerType: ContainerType | null
    numberOfContainers: number | null
  }>(id, (input) => api.post<Booking>(`/api/bookings/${id}/submit`, input))

export const useRecordCarrierConfirmation = (id: string) =>
  useBookingMutation<{
    carrierBookingRef: string
    coLoaderBookingRef: string | null
    vesselName: string
    voyageNumber: string
    confirmedEtd: string
    confirmedEta: string
  }>(id, (input) => api.post<Booking>(`/api/bookings/${id}/carrier-confirmation`, input))

export const useRecordCarrierRejection = (id: string) =>
  useBookingMutation<{ reason: string }>(id, (input) =>
    api.post<Booking>(`/api/bookings/${id}/carrier-rejection`, input))

export const useRecordCounterOffer = (id: string) =>
  useBookingMutation<{
    proposedVessel: string
    proposedVoyage: string
    proposedEtd: string
    proposedEta: string
  }>(id, (input) => api.post<Booking>(`/api/bookings/${id}/counter-offer`, input))

export const useAcceptCounterOffer = (id: string) =>
  useBookingMutation<void>(id, () => api.post<Booking>(`/api/bookings/${id}/counter-offer/accept`))

export const useRejectCounterOffer = (id: string) =>
  useBookingMutation<void>(id, () => api.post<Booking>(`/api/bookings/${id}/counter-offer/reject`))

export const useAcknowledgeEtdVariance = (id: string) =>
  useBookingMutation<void>(id, () => api.post<Booking>(`/api/bookings/${id}/acknowledge-etd-variance`))

export const useSendBookingConfirmation = (id: string) =>
  useBookingMutation<void>(id, () => api.post<Booking>(`/api/bookings/${id}/send-confirmation`))

export const useGenerateTruckDeliveryOrder = (id: string) =>
  useBookingMutation<{ deliveryAddress: string }>(id, (input) =>
    api.post<Booking>(`/api/bookings/${id}/truck-delivery-order`, input))

export const useDispatchTruckDeliveryOrder = (id: string) =>
  useBookingMutation<{ driverId: string | null; truckingVendorId: string | null }>(id, (input) =>
    api.post<Booking>(`/api/bookings/${id}/truck-delivery-order/dispatch`, input))

export const useRecordVesselOverbooking = (id: string) =>
  useBookingMutation<void>(id, () => api.post<Booking>(`/api/bookings/${id}/vessel-overbooking`))

export const useReinstateBooking = (id: string) =>
  useBookingMutation<{
    newVesselName: string
    newVoyageNumber: string
    newEtd: string
    newEta: string
    reason: string | null
  }>(id, (input) => api.post<Booking>(`/api/bookings/${id}/reinstate`, input))

export const useCancelBooking = (id: string) =>
  useBookingMutation<{ reason: string; initiatedBy: CancellationInitiator }>(id, (input) =>
    api.post<Booking>(`/api/bookings/${id}/cancel`, input))

// There is no mark-ITN-filed endpoint any more. Booking.itnFiled is set by an
// event listener when Compliance records a CBP acceptance.
