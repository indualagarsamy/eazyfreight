/** Presentation helpers. Enum labels live here so screens never print SCREAMING_SNAKE. */

/**
 * Industry terms that stay uppercase. Without these, titleCase turns the enum
 * OCEAN_LCL into "Ocean Lcl" and the audit source INTTRA into "Inttra", which reads
 * as a typo to anyone who works in freight.
 */
const ACRONYMS = new Set([
  'AES', 'BAF', 'BOL', 'CAF', 'CBM', 'CBP', 'EEI', 'ETA', 'ETD', 'FCL', 'GP',
  'HC', 'HS', 'INTTRA', 'ISF', 'ITN', 'KG', 'LCL', 'OFAC', 'OOG', 'PSS', 'SDN',
  'SMS', 'TDO', 'TEU', 'THC',
])

export function titleCase(value: string): string {
  return value
    .split('_')
    .map((word) => {
      const upper = word.toUpperCase()
      if (ACRONYMS.has(upper)) return upper
      return upper.charAt(0) + word.toLowerCase().slice(1)
    })
    .join(' ')
}

/**
 * Notification channels. IN_APP is the one titleCase cannot help with — "In App" is
 * not a thing anyone writes.
 */
const CHANNEL_LABELS: Record<string, string> = {
  IN_APP: 'In-app',
  EMAIL: 'Email',
  SMS: 'SMS',
}

export const channelLabel = (channel: string) => CHANNEL_LABELS[channel] ?? titleCase(channel)

const CONTAINER_LABELS: Record<string, string> = {
  TWENTY_GP: "20' GP",
  FORTY_GP: "40' GP",
  FORTY_HC: "40' HC",
  FORTY_FIVE_HC: "45' HC",
}

export const containerLabel = (code: string | null) =>
  code === null ? '—' : (CONTAINER_LABELS[code] ?? code)

export function money(amount: number | null | undefined, currency = 'USD'): string {
  if (amount === null || amount === undefined) return '—'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(amount)
}

export function number(value: number | null | undefined, maximumFractionDigits = 2): string {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat('en-US', { maximumFractionDigits }).format(value)
}

/** ISO date (yyyy-mm-dd) as a short readable date, with no timezone shifting. */
export function date(value: string | null | undefined): string {
  if (!value) return '—'
  const [y, m, d] = value.split('-').map(Number)
  if (!y || !m || !d) return value
  return new Date(y, m - 1, d).toLocaleDateString('en-US', {
    day: 'numeric', month: 'short', year: 'numeric',
  })
}

/**
 * An instant that is really a whole day, shown as that day.
 *
 * <p>Alert deadlines are stored as midnight UTC. Rendering them with dateTime turns a
 * deadline of "the 24th" into "Aug 23, 05:00 PM" for anyone west of Greenwich, which
 * reads as a different — and earlier — deadline than the one the system is enforcing.
 */
export function utcDate(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('en-US', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
  })
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-US', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function relativeTime(value: string | null | undefined): string {
  if (!value) return '—'
  const diffMs = new Date(value).getTime() - Date.now()
  const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000_000], ['month', 2_592_000_000], ['day', 86_400_000],
    ['hour', 3_600_000], ['minute', 60_000],
  ]
  for (const [unit, ms] of units) {
    if (Math.abs(diffMs) >= ms) return formatter.format(Math.round(diffMs / ms), unit)
  }
  return 'just now'
}

/** Whole days from today to an ISO date; negative when in the past. */
export function daysUntil(isoDate: string | null | undefined): number | null {
  if (!isoDate) return null
  const [y, m, d] = isoDate.split('-').map(Number)
  if (!y || !m || !d) return null
  const target = new Date(y, m - 1, d)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86_400_000)
}

/** Short display form of a UUID, for reference columns that would otherwise dominate. */
export const shortId = (id: string | null | undefined) => (id ? id.slice(0, 8) : '—')
