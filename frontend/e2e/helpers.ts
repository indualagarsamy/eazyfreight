import { expect } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'

/**
 * Locators for the shared form and feedback shapes in this UI.
 *
 * The forms pair a plain `<label>` with its control inside a `.field` wrapper rather
 * than wiring them with `for`/`id`, so `getByLabel` finds nothing for text inputs and
 * selects. Rather than scatter that workaround through the specs — or change the
 * production markup to suit the tests — it lives here once.
 */

const exact = (text: string) => new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`)

/** The page a scope belongs to — `Locator` carries one, `Page` is one. */
const pageOf = (scope: Page | Locator): Page =>
  'page' in scope ? (scope as Locator).page() : (scope as Page)

/** The `.field` wrapper whose label is exactly `label`. */
export function field(scope: Page | Locator, label: string): Locator {
  // The `has:` locator is built from the page on purpose. Building it from `scope`
  // would carry that scope's own selector chain into the filter — a dialog-scoped
  // one then looks for a label under a dialog *inside* the field, and matches
  // nothing.
  return scope.locator('.field').filter({
    has: pageOf(scope).locator('label').filter({ hasText: exact(label) }),
  })
}

/** The input, select or textarea of the field labelled `label`. */
export function control(scope: Page | Locator, label: string): Locator {
  return field(scope, label).locator('input, select, textarea')
}

export async function fill(scope: Page | Locator, label: string, value: string) {
  await control(scope, label).fill(value)
}

export async function choose(scope: Page | Locator, label: string, value: string) {
  await control(scope, label).selectOption(value)
}

/** A toast. They self-dismiss, so assert on one promptly after the action that raises it. */
export function toast(page: Page, text: string | RegExp): Locator {
  return page.getByRole('status').filter({ hasText: text })
}

export async function expectToast(page: Page, text: string | RegExp) {
  await expect(toast(page, text)).toBeVisible()
}

/**
 * A status pill in the page header.
 *
 * Matched exactly: the gated buttons put their refusal reason in the header too, as
 * screen-reader text, and "Screening Flagged" is a substring of "Unavailable: Denied
 * party screening flagged this quote".
 */
export function badge(page: Page, text: string): Locator {
  return page.locator('main header').first().getByText(text, { exact: true })
}

/**
 * An ISO date at least `businessDays` weekdays out.
 *
 * Mirrors `com.eazyfreight.common.BusinessDays.add` — weekends only, no holiday
 * calendar — because the five-business-day ETD lead time is enforced server side and
 * a hard-coded date in a test goes stale the week after it is written.
 */
export function businessDaysFromToday(businessDays: number, from = new Date()): string {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  let added = 0
  while (added < businessDays) {
    date.setDate(date.getDate() + 1)
    const day = date.getDay()
    if (day !== 0 && day !== 6) added++
  }
  return toIso(date)
}

export function daysFromToday(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return toIso(date)
}

function toIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/**
 * The card under the `<h2>` with exactly this heading — used to scope an assertion to
 * one section. Exactly, because "Booking" is otherwise also "Carrier booking".
 */
export const card = (page: Page, heading: string): Locator =>
  page.locator('.card').filter({
    has: page.getByRole('heading', { name: heading, exact: true, level: 2 }),
  })

/** An ISO date the way the UI prints it, e.g. 2026-10-05 as "Oct 5, 2026". */
export function readableDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', {
    day: 'numeric', month: 'short', year: 'numeric',
  })
}

/** The aggregate id out of a detail-page URL, for a later navigation. */
export function idFromUrl(url: string): string {
  const id = url.split('/').pop()?.split('?')[0]
  if (!id) throw new Error(`No id in URL: ${url}`)
  return id
}
