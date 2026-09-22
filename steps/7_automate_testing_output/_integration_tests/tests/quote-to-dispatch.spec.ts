import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { loadArazzoWorkflow, runWorkflow } from '../src/arazzoRunner';
import { FRONTEND_BASE_URL } from '../playwright.config';

const ARAZZO_PATH = path.resolve(__dirname, '../../arazzo.yaml');
const WORKFLOW_ID = 'quoteToOutboundDispatch';

/**
 * Runs the real quoteToOutboundDispatch workflow from arazzo.yaml against a
 * live eazyfreight backend (see ../../../../RUNNING.md: `docker compose up
 * -d` then `./gradlew bootRun`). Every request body, path parameter and
 * chained value below comes straight out of arazzo.yaml via arazzoRunner —
 * this file only supplies the fresh, caller-side input values (customer
 * id, cargo detail, etc.) that a real caller of the workflow would provide.
 */
function freshInputs() {
  return {
    customerId: randomUUID(),
    shipperId: randomUUID(),
    consigneeId: randomUUID(),
    shippingMode: 'OCEAN_FCL',
    originPortCode: 'USLAX',
    destinationPortCode: 'CNSHA',
    incoterms: 'FOB',
    currency: 'USD',
    shipperName: 'Acme Exports LLC',
    shipperAddress: '123 Harbor Blvd, Los Angeles, CA',
    shipperCountry: 'US',
    consigneeName: 'Global Imports Co',
    consigneeAddress: '88 Trade St, Shanghai',
    consigneeCountry: 'CN',
    requestedEtd: '2026-11-01',
    pickupAddress: '123 Harbor Blvd, Los Angeles, CA',
    pickupDateTime: '2026-11-01T08:00:00Z',
    deliveryAddress: 'Carrier Yard, Long Beach, CA',
    scheduledPickupDate: '2026-11-01T08:00:00Z',
    scheduledDeliveryDate: '2026-11-01T14:00:00Z',
    carrierId: randomUUID(),
    containerType: 'FORTY_HC',
    numberOfContainers: 1,
    // LogisticsService.dispatchOutbound requires either a driver or a trucking
    // vendor on the dispatch, and a third-party (trucking-vendor) dispatch also
    // requires a vehicleReference for audit — both optional in DispatchTruck's
    // schema, so both must be supplied explicitly rather than left to omission.
    truckingVendorId: randomUUID(),
    vehicleReference: 'TRK-4471',
    carrierBookingRef: `CARR-${randomUUID().slice(0, 8)}`,
    vesselName: 'MSC Playwright',
    voyageNumber: 'V001E',
    confirmedEtd: '2026-11-02',
    confirmedEta: '2026-11-20',
    actor: 'playwright-tests',
    cargoDetails: [
      {
        description: 'Widgets',
        hsCode: '8471.30.0100',
        pieces: 10,
        weightKg: 500,
        lengthCm: 120,
        widthCm: 100,
        heightCm: 100,
        hazmat: false,
        temperatureControlled: false,
        oversized: false,
        valueUsd: 15000,
        marksAndNumbers: 'ACME/001',
      },
    ],
  };
}

test.describe('quoteToOutboundDispatch (arazzo.yaml)', () => {
  test.beforeEach(async ({ request, baseURL }) => {
    try {
      await request.get('/actuator/health', { timeout: 3000 });
    } catch {
      test.skip(true, `Backend not reachable at ${baseURL}. Start it per RUNNING.md (docker compose up -d && ./gradlew bootRun) before running this test.`);
    }
  });

  test('prices, books, confirms, and dispatches a shipment end to end', async ({ request }) => {
    const { doc, operationIndex } = loadArazzoWorkflow(ARAZZO_PATH);

    const result = await runWorkflow(request, doc, operationIndex, WORKFLOW_ID, freshInputs());

    // Each step's successCriteria ($statusCode == 2xx) already threw inside
    // runWorkflow if it failed, so getting here means all 5 calls succeeded.

    // createQuote echoed back the lane/party fields we sent.
    expect(result.steps.createQuote.body).toMatchObject({
      shippingMode: 'OCEAN_FCL',
      originPortCode: 'USLAX',
      destinationPortCode: 'CNSHA',
      incoterms: 'FOB',
    });

    // createBooking carries the quoteId forward and starts unconfirmed.
    expect(result.steps.createBooking.body).toMatchObject({
      quoteId: result.steps.createQuote.outputs.quoteId,
      status: 'BOOKING_REQUESTED',
    });

    // submitBooking / recordCarrierConfirmation move the same booking through its lifecycle.
    expect(result.steps.submitBooking.outputs.bookingId).toBe(result.steps.createBooking.outputs.bookingId);
    expect(result.steps.submitBooking.outputs.bookingStatus).toBe('SUBMITTED_TO_CARRIER');
    expect(result.steps.recordCarrierConfirmation.outputs.bookingStatus).toBe('CONFIRMED_BY_CARRIER');

    // dispatchOutboundTruck only succeeds (per LogisticsService.requireConfirmedBooking)
    // because the booking is now confirmed; it advances the container past NOT_STARTED.
    expect(result.steps.dispatchOutboundTruck.body).toMatchObject({ stage: 'OUTBOUND_DISPATCHED' });
    expect(result.steps.dispatchOutboundTruck.body).toHaveProperty('dispatches.0.pickupAddress', '123 Harbor Blvd, Los Angeles, CA');

    // Workflow-level outputs are exactly what a caller of this Arazzo workflow gets back.
    expect(result.outputs).toMatchObject({
      bookingStatus: 'CONFIRMED_BY_CARRIER',
      logisticsStage: 'OUTBOUND_DISPATCHED',
    });
    expect(result.outputs.quoteId).toEqual(expect.stringMatching(/^[0-9a-f-]{36}$/));
    expect(result.outputs.bookingId).toEqual(expect.stringMatching(/^[0-9a-f-]{36}$/));
    expect(result.outputs.logisticsId).toEqual(expect.stringMatching(/^[0-9a-f-]{36}$/));
  });

  test('dispatchOutboundTruck is refused when a booking has not been carrier-confirmed', async ({ request }) => {
    // Documents the exact business rule that makes createBooking -> dispatchOutbound (skipping
    // submitBooking/recordCarrierConfirmation) fail — see LogisticsService.requireConfirmedBooking.
    const bookingResponse = await request.post('/api/bookings', {
      data: {
        customerId: randomUUID(),
        shipperId: randomUUID(),
        consigneeId: randomUUID(),
        shippingMode: 'OCEAN_FCL',
        originPortCode: 'USLAX',
        destinationPortCode: 'CNSHA',
        incoterms: 'FOB',
        requestedEtd: '2026-11-01',
        cargoDetails: freshInputs().cargoDetails,
      },
    });
    expect(bookingResponse.status()).toBe(201);
    const booking = await bookingResponse.json();

    const dispatchResponse = await request.post(`/api/logistics/bookings/${booking.id}/outbound-dispatch`, {
      data: { pickupAddress: '123 Harbor Blvd, Los Angeles, CA', deliveryAddress: 'Carrier Yard, Long Beach, CA' },
    });

    // GlobalExceptionHandler maps DomainRuleViolationException to 409 Conflict.
    expect(dispatchResponse.status()).toBe(409);
  });

  test('the UI refuses to dispatch outbound on an unconfirmed booking', async ({ request, page, baseURL }) => {
    // Same business rule as the API-level test above, driven through the real
    // frontend (LogisticsDetailPage) instead of a raw HTTP call, so the refusal
    // is verified the way an operator would actually see it: a toast, not a
    // status code. Skips (rather than fails) if the frontend dev server isn't
    // reachable, matching the backend-reachability skip in beforeEach above.
    try {
      await request.get(FRONTEND_BASE_URL, { timeout: 3000 });
    } catch {
      test.skip(true, `Frontend not reachable at ${FRONTEND_BASE_URL}. Start it with 'cd frontend && npm run dev' before running this test.`);
    }

    // Seed via the API — creating the booking has its own dedicated coverage
    // above and in the arazzo-driven test; this test's subject is the dispatch
    // refusal, not booking creation. requestedEta is set explicitly: at the
    // time this was written, BookingService.createBookingRequest passes
    // request.requestedEta() into both the requestedEtd and requestedEta slots
    // of Booking.request(...), so omitting requestedEta (as the sibling API
    // test above does) 500s instead of the 201 this setup needs.
    const bookingResponse = await request.post('/api/bookings', {
      data: {
        customerId: randomUUID(),
        shipperId: randomUUID(),
        consigneeId: randomUUID(),
        shippingMode: 'OCEAN_FCL',
        originPortCode: 'USLAX',
        destinationPortCode: 'CNSHA',
        incoterms: 'FOB',
        requestedEtd: '2026-11-01',
        requestedEta: '2026-11-20',
        cargoDetails: freshInputs().cargoDetails,
      },
    });
    expect(bookingResponse.status()).toBe(201);
    const booking = await bookingResponse.json();

    await page.goto(`${FRONTEND_BASE_URL}/logistics/${booking.id}`);

    // A booking with no container movements yet shows the empty-state entry
    // point rather than the full lifecycle view (see LogisticsDetailPage's
    // ApiError.isNotFound branch) — this is what a fresh, unconfirmed booking
    // looks like, so this is the button an operator would actually reach for.
    await page.getByRole('button', { name: 'Dispatch outbound truck' }).click();

    const dialog = page.getByRole('dialog', { name: 'Dispatch outbound truck' });
    // DispatchModal's <label> has no htmlFor/aria-labelledby linking it to its
    // input (see the shared F() helper in LogisticsDetailPage.tsx), so
    // getByLabel can't find it — driver id, pickup address, and delivery
    // address are the only three textboxes in this dialog, in that DOM order.
    await dialog.getByRole('textbox').nth(2).fill('Carrier Yard, Long Beach, CA');
    await dialog.getByRole('button', { name: 'Dispatch', exact: true }).click();

    // The 409 the API test asserts directly surfaces here as a toast — the
    // dialog stays open (Toast.run only closes it on success), matching how
    // client.ts documents a 409 as the domain refusing the command.
    await expect(page.getByText('Action not allowed')).toBeVisible();
    await expect(page.getByText('No truck is dispatched before the carrier confirms space')).toBeVisible();
    await expect(dialog).toBeVisible();
  });
});
