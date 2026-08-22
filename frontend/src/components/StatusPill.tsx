import { titleCase } from './format'
import styles from './StatusPill.module.css'

export type Tone = 'neutral' | 'info' | 'positive' | 'warning' | 'danger'

const QUOTE_TONES: Record<string, Tone> = {
  DRAFT: 'neutral', SENT: 'info', ACCEPTED: 'positive',
  DECLINED: 'danger', EXPIRED: 'warning',
}

const BOOKING_TONES: Record<string, Tone> = {
  BOOKING_REQUESTED: 'neutral',
  SUBMITTED_TO_CARRIER: 'info',
  COUNTER_OFFER_RECEIVED: 'warning',
  REJECTED_BY_CARRIER: 'danger',
  CONFIRMED_BY_CARRIER: 'positive',
  CUSTOMER_CONFIRMED: 'positive',
  VESSEL_OVERBOOKED: 'warning',
  CANCELLED: 'danger',
}

const SCREENING_TONES: Record<string, Tone> = {
  PENDING: 'warning', CLEARED: 'positive', FLAGGED: 'danger',
}

const TDO_TONES: Record<string, Tone> = {
  GENERATED: 'neutral', DISPATCHED: 'info', PICKED_UP: 'info', DELIVERED: 'positive',
}

const FILING_TONES: Record<string, Tone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', ACCEPTED: 'positive',
  REJECTED: 'danger', CANCELLED: 'danger',
  ORIGINAL: 'neutral', AMENDMENT: 'info',
}

const LOGISTICS_TONES: Record<string, Tone> = {
  NOT_STARTED: 'neutral', OUTBOUND_DISPATCHED: 'info', AT_CUSTOMER: 'info',
  LOADING_COMPLETE: 'info', SEALED: 'info', INBOUND_DISPATCHED: 'info',
  AT_TERMINAL: 'positive', UNDER_CBP_EXAMINATION: 'danger',
  LOADED_ON_VESSEL: 'positive', DEPARTED: 'positive',
  OUTBOUND: 'neutral', INBOUND: 'info',
  CUSTOMER_ISSUED: 'neutral', CUSTOMS_ISSUED: 'warning',
  PICKED_UP: 'info', RELEASED: 'positive', ADDITIONAL_HOLD: 'danger', SEIZED: 'danger',
}

const REGISTRY = {
  ...QUOTE_TONES, ...BOOKING_TONES, ...SCREENING_TONES, ...TDO_TONES, ...FILING_TONES, ...LOGISTICS_TONES,
}

interface Props {
  status: string
  tone?: Tone
  label?: string
  size?: 'sm' | 'md'
}

export function StatusPill({ status, tone, label, size = 'md' }: Props) {
  const resolved = tone ?? REGISTRY[status] ?? 'neutral'
  return (
    <span className={`${styles.pill} ${styles[resolved]} ${size === 'sm' ? styles.sm : ''}`}>
      <span className={styles.dot} aria-hidden />
      {label ?? titleCase(status)}
    </span>
  )
}
