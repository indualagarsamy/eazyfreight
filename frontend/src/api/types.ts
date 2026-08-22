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
  valueUsd: number | null
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
  totalValueUsd: number
  createdAt: string
  createdBy: string
  lastModifiedAt: string
  lastModifiedBy: string
  carrierBooking: CarrierBooking | null
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

// ---------------------------------------------------------------- compliance

export type FilingType = 'ORIGINAL' | 'AMENDMENT' | 'CANCELLATION'
export type FilingStatus = 'DRAFT' | 'SUBMITTED' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'

export interface ItnRecord {
  id: string
  itnNumber: string
  issuedAt: string
  active: boolean
  simulated: boolean
  supersededByItnId: string | null
  recordedAt: string
  recordedBy: string
}

export interface EEIFilingHistoryEntry {
  id: string
  sequenceNumber: number
  fromStatus: FilingStatus | null
  toStatus: FilingStatus
  occurredAt: string
  actor: string
  detail: string | null
}

export interface ExportLicense {
  id: string
  licenseNumber: string
  issuingAuthority: string
  licenseType: string
  commodityEccn: string | null
  validFrom: string
  validUntil: string
  valueAuthorized: number | null
}

export interface EEIFiling {
  id: string
  bookingId: string
  filingReference: string
  filingType: FilingType
  parentFilingId: string | null
  status: FilingStatus
  shipperName: string | null
  shipperEin: string | null
  shipperAddress: string | null
  consigneeName: string | null
  consigneeAddress: string | null
  consigneeCountry: string | null
  scheduleBNumber: string | null
  scheduleBTranslated: boolean
  commodityDescription: string | null
  quantityValue: number | null
  quantityUnit: string | null
  valueUsd: number | null
  carrierScac: string | null
  vesselName: string | null
  voyageNumber: string | null
  portOfExportCode: string | null
  countryOfDestination: string | null
  estimatedEtd: string | null
  submittedAt: string | null
  submittedBy: string | null
  acceptedAt: string | null
  rejectedAt: string | null
  rejectionReasonCode: string | null
  rejectionReasonDescription: string | null
  cancelledAt: string | null
  cancellationReason: string | null
  aesSubmissionReference: string | null
  simulated: boolean
  amendmentReason: string | null
  licenseRequired: boolean
  filingRequired: boolean
  missingRequiredFields: string[]
  activeItnNumber: string | null
  createdAt: string
  exportLicense: ExportLicense | null
  itnRecords: ItnRecord[]
  history: EEIFilingHistoryEntry[]
}

export interface FilingSystemStatus {
  simulated: boolean
  notice: string
}

// ---------------------------------------------------------------- logistics

export type MovementType = 'OUTBOUND' | 'INBOUND'
export type DispatchStatus = 'DISPATCHED' | 'PICKED_UP' | 'DELIVERED' | 'FAILED'
export type AddressType = 'CARRIER_YARD' | 'CUSTOMER_PREMISES' | 'PORT_TERMINAL'
export type ContainerSource = 'CARRIER_YARD' | 'MANUAL_ENTRY'
export type SealSource = 'CUSTOMER_ISSUED' | 'CUSTOMS_ISSUED'
export type SealDeactivationReason = 'CUSTOMS_INSPECTION' | 'DAMAGED_SEAL'
export type ExaminationResult = 'RELEASED' | 'ADDITIONAL_HOLD' | 'SEIZED'
export type LogisticsStage =
  | 'NOT_STARTED' | 'OUTBOUND_DISPATCHED' | 'AT_CUSTOMER' | 'LOADING_COMPLETE'
  | 'SEALED' | 'INBOUND_DISPATCHED' | 'AT_TERMINAL' | 'UNDER_CBP_EXAMINATION'
  | 'LOADED_ON_VESSEL' | 'DEPARTED'

export interface SealRecordView {
  id: string
  sealNumber: string
  sealSource: SealSource
  active: boolean
  issuedAt: string
  deactivatedAt: string | null
  deactivationReason: SealDeactivationReason | null
  replacedBySealId: string | null
  recordedBy: string
}

export interface DispatchView {
  id: string
  movementType: MovementType
  tdoReference: string
  driverId: string | null
  truckingVendorId: string | null
  vehicleReference: string | null
  pickupAddress: string
  pickupAddressType: AddressType
  deliveryAddress: string
  deliveryAddressType: AddressType
  scheduledPickupDate: string | null
  actualPickupDate: string | null
  scheduledDeliveryDate: string | null
  actualDeliveryDate: string | null
  status: DispatchStatus
  dispatchedAt: string
  dispatchedBy: string
  deliveryReceiptReference: string | null
  notes: string | null
}

export interface TerminalView {
  id: string
  gateReceiptNumber: string
  terminalName: string
  acceptedAt: string
  earliestAcceptanceDate: string | null
  vesselCutOffDate: string | null
  storageFeeApplies: boolean
  daysEarly: number | null
  storageFeeDailyRate: number | null
  estimatedStorageFee: number
}

export interface ExaminationView {
  id: string
  holdPlacedAt: string
  examinationCompletedAt: string | null
  result: ExaminationResult | null
  originalSealId: string | null
  replacementSealId: string | null
  cbpOfficerId: string | null
  notes: string | null
  open: boolean
}

export interface ActualCargoView {
  id: string
  actualWeightKg: number
  actualPieces: number
  actualCbm: number | null
  bookedWeightKg: number
  bookedPieces: number
  bookedCbm: number | null
  weightVarianceKg: number
  divergesMaterially: boolean
  recordedAt: string
  recordedBy: string
}

export interface Logistics {
  id: string
  bookingId: string
  containerNumber: string | null
  containerType: ContainerType | null
  source: ContainerSource | null
  assignedAt: string | null
  assignedBy: string | null
  stage: LogisticsStage
  itnReceived: boolean
  itnNumber: string | null
  inboundBlockedReason: string | null
  documentationPreconditionsMet: boolean
  loadingCompletedAt: string | null
  loadedOnVesselAt: string | null
  vesselDepartedAt: string | null
  createdAt: string
  activeSealNumber: string | null
  sealRecords: SealRecordView[]
  dispatches: DispatchView[]
  examinations: ExaminationView[]
  terminalAcceptance: TerminalView | null
  actualCargoDetails: ActualCargoView | null
}

export interface AwaitingDispatch {
  bookingId: string
  bookingReference: string
  requestedEtd: string | null
  confirmedEtd: string | null
  pickupAddress: string | null
}

export interface ItnGate {
  clear: boolean
  reason: string | null
}

// ------------------------------------------------------------- documentation

export type InstructionsStatus = 'DRAFT' | 'APPROVED' | 'SENT' | 'QUERIED' | 'SUPERSEDED'
export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'DISCREPANCY_RAISED' | 'CORRECTED'
export type HouseBOLStatus = 'ISSUED' | 'VOIDED'
export type ReleaseType = 'ORIGINAL_BOL' | 'TELEX_RELEASE' | 'SEA_WAYBILL'
export type FreightTerms = 'PREPAID' | 'COLLECT'
export type DistributionRecipient = 'SHIPPER' | 'CONSIGNEE_AGENT' | 'NOTIFY_PARTY'
export type DistributionChannel = 'EMAIL' | 'PORTAL' | 'COURIER'

export interface Preconditions {
  bookingId: string
  met: boolean
  containerNumber: string | null
  sealNumber: string | null
  itnNumber: string | null
  carrierBookingRef: string | null
  missing: string[]
}

export interface Instructions {
  id: string
  bookingId: string
  instructionsReference: string
  status: InstructionsStatus
  supersedesInstructionsId: string | null
  carrierBookingRef: string | null
  containerNumber: string
  sealNumber: string
  itnNumber: string
  shipperName: string
  shipperAddress: string | null
  consigneeName: string
  consigneeAddress: string | null
  notifyPartyName: string | null
  notifyPartyAddress: string | null
  portOfLoadingCode: string
  portOfDischargeCode: string
  vesselName: string | null
  voyageNumber: string | null
  cargoDescription: string
  hsCode: string | null
  actualWeightKg: number | null
  actualPieces: number | null
  actualCbm: number | null
  marksAndNumbers: string | null
  freightTerms: FreightTerms
  draftedAt: string
  draftedBy: string
  approvedAt: string | null
  approvedBy: string | null
  sentAt: string | null
  documentationCutOffDate: string | null
  sentAfterCutOff: boolean
  carrierQuery: string | null
}

export interface MasterBOL {
  id: string
  bookingId: string
  instructionsId: string
  masterBolNumber: string
  issuedByCarrierAt: string | null
  receivedAt: string
  receivedBy: string
  verificationStatus: VerificationStatus
  verifiedAt: string | null
  verifiedBy: string | null
  discrepancyFields: string[]
  discrepancyRaisedAt: string | null
  discrepancyResolvedAt: string | null
  documentFileReference: string | null
  verified: boolean
}

export interface Originals {
  id: string
  originalsIssued: number
  originalsSurrendered: number
  outstanding: number
  allSurrendered: boolean
  releasedAt: string | null
  releasedTo: string | null
  courierReference: string | null
  surrenderedAt: string | null
}

export interface Distribution {
  id: string
  recipient: DistributionRecipient
  recipientName: string | null
  recipientAddress: string | null
  channel: DistributionChannel
  revisionNumber: number
  sentAt: string
  sentBy: string
  reference: string | null
}

export interface HouseBOL {
  id: string
  bookingId: string
  masterBolId: string
  houseBolNumber: string
  revisionNumber: number
  active: boolean
  status: HouseBOLStatus
  releaseType: ReleaseType
  releaseTypeConfirmedAt: string | null
  releaseTypeConfirmedBy: string | null
  shipperNameSnapshot: string
  shipperAddressSnapshot: string | null
  consigneeNameSnapshot: string
  consigneeAddressSnapshot: string | null
  notifyPartyNameSnapshot: string | null
  notifyPartyAddressSnapshot: string | null
  portOfLoadingCode: string
  portOfDischargeCode: string
  vesselName: string | null
  voyageNumber: string | null
  containerNumber: string
  sealNumber: string
  cargoDescription: string
  hsCode: string | null
  weightKg: number | null
  pieces: number | null
  cbm: number | null
  marksAndNumbers: string | null
  freightTerms: FreightTerms
  issuedAt: string
  issuedBy: string
  voidedAt: string | null
  supersededByHouseBolId: string | null
  amendmentReason: string | null
  pdfReference: string | null
  amendmentBlockedReason: string | null
  originals: Originals | null
  distributions: Distribution[]
}
