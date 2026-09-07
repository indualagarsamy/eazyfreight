import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { test, expect } from '@playwright/test';
import { loadArazzoWorkflow, runWorkflow } from '../src/arazzoRunner';

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
});
