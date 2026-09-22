import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { businessDaysFromToday, card, daysFromToday, fill, readableDate } from './helpers'

/**
 * What a booking records versus what operations typed.
 *
 * Both tests here fail today, and both are asserting the behaviour the forms and the
 * field names promise. They are kept apart from the lifecycle flow on purpose: the
 * state machine is sound, it is the values travelling through it that are wrong, and
 * the two failures point at one method — `BookingService.createBooking`.
 */

const ETD = businessDaysFromToday(10)
const ETA = daysFromToday(35)

test.describe('What a booking records', () => {
  /**
   * `createBooking` passes `request.requestedEta()` for both the ETD and the ETA
   * arguments of `Booking.request`, so the requested ETD is dropped and the booking
   * sails on the ETA. Everything downstream inherits it: the five-business-day lead
   * check, the ETD variance gate on confirmation, and the customer notification that
   * gate exists to force.
   */
  test('the requested ETD that was entered, not the ETA', async ({ page }) => {
    await createBooking(page)
    await expect(card(page, 'Booking')).toContainText(`Requested ETD${readableDate(ETD)}`)
  })

  /**
   * The form asks for kilograms and centimetres, the API field is named `weightKg`,
   * and `createBooking` then multiplies every one of them by an imperial conversion
   * factor. 2,400 kg is stored as 1,088.6 kg — which is also what the container
   * payload check at submission and the Master BOL weights will use.
   */
  test('cargo weight in the unit the form asks for', async ({ page }) => {
    await createBooking(page)
    await expect(card(page, 'Cargo')).toContainText('2,400 kg')
    await expect(card(page, 'Booking')).toContainText('Total weight2,400 kg')
  })
})

/** A booking with dates and cargo that are unambiguous when read back. */
async function createBooking(page: Page) {
  await page.goto('/bookings/new')

  await fill(page, 'Requested ETD', ETD)
  await fill(page, 'Requested ETA', ETA)
  await fill(page, 'Pickup address', 'Plot 44, MIDC Andheri East, Mumbai 400093')

  await fill(page, 'Description', 'Cotton apparel, mixed sizes')
  await fill(page, 'HS code', '6109.10.0010')
  await fill(page, 'Pieces', '120')
  await fill(page, 'Weight (kg)', '2400')
  await fill(page, 'Declared value (USD)', '48000')
  await fill(page, 'Length (cm)', '240')
  await fill(page, 'Width (cm)', '120')
  await fill(page, 'Height (cm)', '180')

  await page.getByRole('button', { name: 'Create booking' }).click()
  await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]{36}$/)
}
