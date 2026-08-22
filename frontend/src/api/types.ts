/**
 * Mirrors the service DTOs. Kept hand-written rather than generated so the shapes
 * the UI depends on are explicit and reviewable; if the service grows an OpenAPI
 * document these should be generated from it instead.
 */

export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED'
export type ScreeningStatus = 'PENDING' | 'CLEARED' | 'FLAGGED'
export type QuoteShippingMode = 'OCEAN_FCL' | 'OCEAN_LCL' | 'AIR'
export type ChargeUnit = 'TEU' | 'CBM' | 'KG' | 'FLAT'
export type QuoteLineType =
  | 'BASE_FREIGHT' | 'BAF' | 'CAF' | 'PSS'
  | 'THC_ORIGIN' | 'THC_DESTINATION' | 'DOC_FEE' | 'INSURANCE'

export interface QuoteLine {
  id: string
  lineType: QuoteLineType
  description: string
  buyRate: number
  sellRate: number
  currency: string
  quantity: number
  unit: ChargeUnit
  amount: number
}

export interface CargoDetail {
  id: string
  description: string
  hsCode: string
  pieces: number
  weightKg: number
  lengthCm: number
  widthCm: number
  heightCm: number
  volumetricWeightCbm: number | null
  volumetricWeightKg: number | null
  chargeableWeight: number | null
  chargeableUnit: ChargeUnit | null
  hazmat: boolean
  temperatureControlled: boolean
  oversized: boolean
}

export interface Quote {
  id: string
  quoteReference: string
  customerId: string
  status: QuoteStatus
  shippingMode: QuoteShippingMode
  originPortCode: string
  destinationPortCode: string
  incoterms: string
  selectedCarrierId: string | null
  screeningStatus: ScreeningStatus
  screeningReferenceId: string | null
  requestedEtd: string | null
  validFrom: string | null
  validUntil: string | null
  rateValidUntil: string | null
  createdAt: string
  sentAt: string | null
  acceptedAt: string | null
  declinedAt: string | null
  expiredAt: string | null
  declineReason: string | null
  notes: string | null
  totalBuyRate: number
  totalSellRate: number
  margin: number
  currency: string
  spotRate: boolean
  specialHandling: string | null
  insuranceRequired: boolean
  quoteLines: QuoteLine[]
  cargoDetails: CargoDetail[]
}

export type BookingStatus =
  | 'BOOKING_REQUESTED' | 'SUBMITTED_TO_CARRIER' | 'REJECTED_BY_CARRIER'
  | 'COUNTER_OFFER_RECEIVED' | 'CONFIRMED_BY_CARRIER' | 'CUSTOMER_CONFIRMED'
  | 'VESSEL_OVERBOOKED' | 'CANCELLED'

export type BookingShippingMode = 'OCEAN_FCL' | 'OCEAN_LCL'
export type BookingSourceType = 'DIRECT_CARRIER' | 'CO_LOADER'
export type ContainerType = 'TWENTY_GP' | 'FORTY_GP' | 'FORTY_HC' | 'FORTY_FIVE_HC'
export type TruckDeliveryOrderStatus = 'GENERATED' | 'DISPATCHED' | 'PICKED_UP' | 'DELIVERED'
export type StatusChangeSource = 'MANUAL' | 'INTTRA' | 'SYSTEM'
export type CancellationInitiator = 'CUSTOMER' | 'CARRIER_OVERBOOKING' | 'OPERATIONS'

export interface CarrierBooking {
  id: string
  bookingSourceType: BookingSourceType
  carrierId: string
  carrierBookingRef: string | null
  coLoaderBookingRef: string | null
  vesselName: string | null
  voyageNumber: string | null
  confirmedEtd: string | null
  confirmedEta: string | null
  containerType: ContainerType | null
  numberOfContainers: number | null
  submittedAt: string | null
  confirmedAt: string | null
  confirmedBy: string | null
}

export interface TruckDeliveryOrder {
  id: string
  tdoReference: string
  pickupAddress: string
  deliveryAddress: string
  pickupDateTime: string | null
  driverId: string | null
  truckingVendorId: string | null
  status: TruckDeliveryOrderStatus
  generatedAt: string
  dispatchedAt: string | null
}

export interface BookingStatusHistoryEntry {
  id: string
  sequenceNumber: number
  fromStatus: BookingStatus | null
  toStatus: BookingStatus
  changedAt: string
  changedBy: string
  reason: string | null
  source: StatusChangeSource
}

export interface BookingReinstatement {
  id: string
  previousVessel: string | null
  previousVoyage: string | null
  previousEtd: string | null
  previousEta: string | null
  newVesselName: string
  newVoyageNumber: string
  newEtd: string
  newEta: string
  reinstatedAt: string
  reinstatedBy: string
  reason: string
}

export interface BookingCargoDetail {
  id: string
  description: string
  hsCode: string
  pieces: number
  weightKg: number
  lengthCm: number
  widthCm: number
  heightCm: number
  cbm: number | null
  hazmat: boolean
  temperatureControlled: boolean
  oversized: boolean
  marksAndNumbers: string | null
}

export interface Booking {
  id: string
  bookingReference: string
  quoteId: string | null
  customerId: string
  shipperId: string
  consigneeId: string
  notifyPartyId: string | null
  alsoNotifyId: string | null
  status: BookingStatus
  shippingMode: BookingShippingMode
  originPortCode: string
  destinationPortCode: string
  incoterms: string
  requestedEtd: string
  requestedEta: string | null
  transportRequired: boolean
  pickupAddress: string | null
  pickupDateTime: string | null
  specialInstructions: string | null
  marksAndNumbers: string | null
  cancellationReason: string | null
  cancellationInitiatedBy: CancellationInitiator | null
  cancelledAt: string | null
  requiresCustomerEtdNotification: boolean
  etdVarianceAcknowledgedAt: string | null
  confirmationSentAt: string | null
  itnFiled: boolean
  totalWeightKg: number
  createdAt: string
  createdBy: string
  lastModifiedAt: string
  lastModifiedBy: string
  carrierBooking: CarrierBooking | null
  truckDeliveryOrder: TruckDeliveryOrder | null
  cargoDetails: BookingCargoDetail[]
  statusHistory: BookingStatusHistoryEntry[]
  reinstatements: BookingReinstatement[]
}

/** The service's shared error envelope. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  fieldErrors: Record<string, string> | null
}
