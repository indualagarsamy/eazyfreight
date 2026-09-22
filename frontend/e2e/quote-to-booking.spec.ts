import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import {
  badge, businessDaysFromToday, card, choose, control, daysFromToday,
  expectToast, fill, idFromUrl,
} from './helpers'

/**
 * The ocean export happy path, driven through the screens an operator uses:
 * enquiry → priced quotation → sent → accepted → booking → with the carrier →
 * confirmed → customer told → EEI filing opened.
 *
 * Every assertion is on state the service returned, not on something the form typed.
 * When the header badge reads "Accepted", the aggregate accepted the command.
 */

const ETD = businessDaysFromToday(10)
const ETA = daysFromToday(35)
const CONFIRMED_ETA = daysFromToday(45)

test.describe('Quote to booking', () => {
  test('an enquiry becomes a booking confirmed with the carrier', async ({ page }) => {
    // ---------------------------------------------------------------- quote
    await page.goto('/quotes')
    await expect(page.getByRole('heading', { name: 'Quotes', level: 1 })).toBeVisible()
    await page.getByRole('link', { name: 'New quote' }).click()

    await expect(page.getByRole('heading', { name: 'New quote', level: 1 })).toBeVisible()
    await fillQuoteForm(page, {
      shipperName: 'Mumbai Textiles Pvt Ltd',
      consigneeName: 'Singapore Trading Pte Ltd',
    })
    await page.getByRole('button', { name: 'Create quote' }).click()

    await expect(page).toHaveURL(/\/quotes\/[0-9a-f-]{36}$/)
    const quoteId = idFromUrl(page.url())
    const quoteReference = (await page.getByRole('heading', { level: 1 }).innerText()).trim()
    expect(quoteReference).toMatch(/^Q-\d{4}-\d{5}$/)

    // Screening runs as part of creating the quote, not as a follow-up step.
    await expect(badge(page, 'Screening Cleared')).toBeVisible()
    await expect(badge(page, 'Draft')).toBeVisible()

    // A draft with no rate lines cannot be sent, whatever the operator clicks.
    await expect(page.getByRole('button', { name: 'Send to customer' })).toBeDisabled()

    // Pricing: buy and sell per line, so margin is a property of the quote.
    await page.getByRole('button', { name: 'Price quotation' }).click()
    const pricing = page.getByRole('dialog', { name: 'Price quotation' })
    await pricing.getByPlaceholder('Buy').fill('800')
    await pricing.getByPlaceholder('Sell').fill('1150')
    await pricing.getByPlaceholder('Qty').fill('12.5')
    await fill(pricing, 'Validity (days)', '30')
    await fill(pricing, 'Rate valid until', daysFromToday(21))
    await pricing.getByRole('button', { name: 'Save pricing' }).click()
    await expect(pricing).toBeHidden()

    // 12.5 CBM at 1150 sell against 800 buy — the margin the service computed.
    await expect(page.getByRole('row', { name: /Total/ })).toContainText('$14,375.00')
    await expect(page.getByRole('row', { name: /Margin/ })).toContainText('$4,375.00')

    await page.getByRole('button', { name: 'Send to customer' }).click()
    await expectToast(page, 'Quotation sent')
    await expect(badge(page, 'Sent')).toBeVisible()

    await page.getByRole('button', { name: 'Accept', exact: true }).click()
    await expectToast(page, 'Quote accepted')
    await expect(badge(page, 'Accepted')).toBeVisible()

    // -------------------------------------------------------------- booking
    // Reached by URL because no screen links a quote to its booking yet: the quote
    // holds shipper and consignee as screening names only, so operations has to
    // supply party identifiers here.
    await page.goto(`/bookings/new?quoteId=${quoteId}`)
    await expect(page.getByText('Prefilled from quote')).toContainText(quoteReference)

    // Carried across from the quote rather than retyped.
    await expect(control(page, 'Origin port')).toHaveValue('INBOM')
    await expect(control(page, 'Requested ETD')).toHaveValue(ETD)

    await fill(page, 'Requested ETA', ETA)
    await fill(page, 'Pickup address', 'Plot 44, MIDC Andheri East, Mumbai 400093')
    // Over $2,500, which makes the EEI filing mandatory.
    await fill(page, 'Declared value (USD)', '48000')
    await page.getByRole('button', { name: 'Create booking' }).click()

    await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]{36}$/)
    const bookingReference = (await page.getByRole('heading', { level: 1 }).innerText()).trim()
    expect(bookingReference).toMatch(/^EF-\d{4}-\d{5}$/)
    await expect(badge(page, 'Booking Requested')).toBeVisible()

    // The carrier's reference is externally issued, so it cannot be recorded before
    // the request has gone out.
    await expect(page.getByRole('button', { name: 'Record confirmation' })).toBeDisabled()

    await page.getByRole('button', { name: 'Submit to carrier' }).click()
    const submit = page.getByRole('dialog', { name: 'Submit to carrier' })
    await choose(submit, 'Booked through', 'DIRECT_CARRIER')
    await submit.getByRole('button', { name: 'Submit', exact: true }).click()
    await expect(submit).toBeHidden()
    await expect(badge(page, 'Submitted To Carrier')).toBeVisible()

    await page.getByRole('button', { name: 'Record confirmation' }).click()
    const confirmation = page.getByRole('dialog', { name: 'Record carrier confirmation' })
    await fill(confirmation, 'Carrier booking ref', 'MAEU987654')
    await fill(confirmation, 'Vessel', 'MAERSK SEALAND')
    await fill(confirmation, 'Voyage', '024W')
    // The field defaults to the booking's own requested ETD, so confirming on that
    // date leaves no variance and no customer-notification gate. Whether that date is
    // the one operations typed is a separate question — see booking-integrity.spec.ts.
    await fill(confirmation, 'Confirmed ETA', CONFIRMED_ETA)
    await confirmation.getByRole('button', { name: 'Record confirmation' }).click()
    await expect(confirmation).toBeHidden()

    await expect(badge(page, 'Confirmed By Carrier')).toBeVisible()
    const carrierCard = card(page, 'Carrier booking')
    await expect(carrierCard).toContainText('MAEU987654')
    await expect(carrierCard).toContainText('MAERSK SEALAND')
    await expect(carrierCard).toContainText('024W')
    await expect(page.getByText('Confirmed ETD moved more than two business days')).toHaveCount(0)

    await page.getByRole('button', { name: 'Send confirmation' }).click()
    await expectToast(page, 'Confirmation sent to customer')
    await expect(badge(page, 'Customer Confirmed')).toBeVisible()

    // ----------------------------------------------------------- compliance
    // The ITN gates both the inbound truck dispatch and the Master BOL instructions,
    // so the filing is the first thing raised once the customer is confirmed.
    await card(page, 'Export compliance').getByRole('button', { name: 'Open EEI filing' }).click()
    await expectToast(page, 'EEI filing opened')
    await expect(card(page, 'Export compliance').getByRole('link')).toContainText(/^EEI-/)

    // The audit trail holds every transition and who made it, not just where the
    // booking ended up.
    const audit = card(page, 'Audit trail')
    await expect(audit).toContainText('Booking Requested')
    await expect(audit).toContainText('Submitted To Carrier')
    await expect(audit).toContainText('Confirmed By Carrier')
    await expect(audit).toContainText('ops.jane')
  })

  test('denied party screening halts a quote before it can be sent', async ({ page }) => {
    await page.goto('/quotes/new')
    await fillQuoteForm(page, {
      shipperName: 'DENIED PARTY TRADING LLC',
      consigneeName: 'Singapore Trading Pte Ltd',
    })
    await page.getByRole('button', { name: 'Create quote' }).click()

    await expect(page).toHaveURL(/\/quotes\/[0-9a-f-]{36}$/)
    await expect(badge(page, 'Screening Flagged')).toBeVisible()
    await expect(page.getByText('Halted by denied party screening.')).toBeVisible()

    // Disabled with the reason given, rather than offered and then refused.
    const send = page.getByRole('button', { name: 'Send to customer' })
    await expect(send).toBeDisabled()
    await expect(send).toHaveAttribute('title', 'Denied party screening flagged this quote')
  })
})

async function fillQuoteForm(
  page: Page,
  parties: { shipperName: string; consigneeName: string },
) {
  await choose(page, 'Mode', 'OCEAN_LCL')
  await fill(page, 'Origin port', 'INBOM')
  await fill(page, 'Destination port', 'SGSIN')
  await choose(page, 'Incoterms', 'FOB')
  await fill(page, 'Requested ETD', ETD)
  await fill(page, 'Currency', 'USD')

  await fill(page, 'Shipper name', parties.shipperName)
  await fill(page, 'Shipper address', '14 Marine Drive, Mumbai')
  await fill(page, 'Shipper country', 'IN')
  await fill(page, 'Consignee name', parties.consigneeName)
  await fill(page, 'Consignee address', '9 Raffles Place, Singapore')
  await fill(page, 'Consignee country', 'SG')

  await fill(page, 'Description', 'Cotton apparel, mixed sizes')
  await fill(page, 'HS code', '6109.10.0010')
  await fill(page, 'Pieces', '120')
  await fill(page, 'Weight (kg)', '2400')
  await fill(page, 'Length (cm)', '240')
  await fill(page, 'Width (cm)', '120')
  await fill(page, 'Height (cm)', '180')
}
