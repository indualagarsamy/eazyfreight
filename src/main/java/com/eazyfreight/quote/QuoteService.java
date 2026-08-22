package com.eazyfreight.quote;

import com.eazyfreight.common.BusinessDays;
import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.exception.DomainRuleViolationException;
import com.eazyfreight.exception.QuoteNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Quote context.
 *
 * <p>Deliberately thin: it loads the aggregate, calls one command method on it, and
 * saves. Business rules live on {@link Quote}, not here — the opposite of the
 * monolith's ShipmentService, where a single class carried quoting, booking,
 * compliance and invoicing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QuoteService {

    private static final int MIN_ETD_LEAD_BUSINESS_DAYS = 5;

    private final QuoteRepository quoteRepository;
    private final RateRepository rateRepository;
    private final LaneRepository laneRepository;
    private final DeniedPartyScreeningClient screeningClient;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // ---------------------------------------------------------------- commands

    /**
     * Logs a customer inquiry and immediately runs denied party screening. Screening
     * is part of creating the quote rather than an optional follow-up step, so a
     * quote can never reach a customer unscreened.
     */
    @Transactional
    public QuoteResponse createQuoteRequest(CreateQuoteRequest request) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        if (request.requestedEtd() != null) {
            LocalDate earliest = BusinessDays.add(today, MIN_ETD_LEAD_BUSINESS_DAYS);
            if (request.requestedEtd().isBefore(earliest)) {
                throw new DomainRuleViolationException(
                        "requestedEtd must be at least " + MIN_ETD_LEAD_BUSINESS_DAYS
                                + " business days out (earliest is " + earliest + ")");
            }
        }

        warnOnDuplicateOpenQuote(request);

        Quote quote = Quote.request(
                referenceGenerator.next(ReferenceGenerator.QUOTE_PREFIX),
                request.customerId(),
                request.shippingMode(),
                request.originPortCode(),
                request.destinationPortCode(),
                request.incoterms(),
                request.requestedEtd(),
                request.specialHandling(),
                request.insuranceRequired(),
                request.currency(),
                now
        );

        request.cargoDetails().forEach(detail -> quote.addCargoDetail(toCargoDetail(detail)));

        runScreening(quote, request, now);

        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    /** Re-runs screening — used when the provider was unavailable on the first attempt. */
    @Transactional
    public QuoteResponse rescreen(UUID quoteId, CreateQuoteRequest parties) {
        Quote quote = getQuoteOrThrow(quoteId);
        runScreening(quote, parties, clock.instant());
        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteResponse buildQuotation(UUID quoteId, BuildQuotationRequest request) {
        Quote quote = getQuoteOrThrow(quoteId);
        LocalDate today = LocalDate.now(clock);

        List<QuoteLine> lines = request.lines().stream()
                .map(line -> QuoteLine.builder()
                        .lineType(line.lineType())
                        .description(line.description())
                        .buyRate(line.buyRate())
                        .sellRate(line.sellRate())
                        .currency(quote.getCurrency())
                        .quantity(line.quantity())
                        .unit(line.unit())
                        .build())
                .toList();

        quote.build(
                lines,
                today,
                today.plusDays(request.validityDays()),
                request.rateValidUntil(),
                request.spotRate(),
                request.notes(),
                clock.instant()
        );

        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteResponse send(UUID quoteId) {
        Quote quote = getQuoteOrThrow(quoteId);
        quote.send(LocalDate.now(clock), clock.instant());
        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteResponse accept(UUID quoteId, AcceptQuoteRequest request) {
        Quote quote = getQuoteOrThrow(quoteId);
        quote.accept(request.selectedCarrierId(), LocalDate.now(clock), clock.instant());
        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteResponse decline(UUID quoteId, DeclineQuoteRequest request) {
        Quote quote = getQuoteOrThrow(quoteId);
        quote.decline(request == null ? null : request.reason(), clock.instant());
        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteResponse expire(UUID quoteId) {
        Quote quote = getQuoteOrThrow(quoteId);
        quote.expire(clock.instant());
        return QuoteResponse.fromEntity(quoteRepository.save(quote));
    }

    /**
     * Lapses every open quote whose validity has run out. Intended to be driven by
     * the Alerts track once that context exists; exposed here so expiry is a system
     * behaviour rather than something nobody ever does.
     */
    @Transactional
    public int expireLapsedQuotes() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        List<Quote> lapsed = quoteRepository
                .findByStatusAndValidUntilLessThanEqual(QuoteStatus.SENT, today.minusDays(1));

        lapsed.forEach(quote -> {
            quote.expire(now);
            quoteRepository.save(quote);
        });
        return lapsed.size();
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public QuoteResponse findById(UUID quoteId) {
        return QuoteResponse.fromEntity(getQuoteOrThrow(quoteId));
    }

    @Transactional(readOnly = true)
    public QuoteResponse findByReference(String quoteReference) {
        return quoteRepository.findByQuoteReference(quoteReference)
                .map(QuoteResponse::fromEntity)
                .orElseThrow(() -> new QuoteNotFoundException(quoteReference));
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> findAll() {
        return quoteRepository.findAll().stream().map(QuoteResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> findByCustomer(UUID customerId, QuoteStatus status) {
        List<Quote> quotes = status == null
                ? quoteRepository.findByCustomerId(customerId)
                : quoteRepository.findByCustomerIdAndStatus(customerId, status);
        return quotes.stream().map(QuoteResponse::fromEntity).toList();
    }

    /** Quotes still awaiting a customer decision. */
    @Transactional(readOnly = true)
    public List<QuoteResponse> findOpen() {
        return quoteRepository.findByStatusIn(List.of(QuoteStatus.DRAFT, QuoteStatus.SENT)).stream()
                .map(QuoteResponse::fromEntity)
                .toList();
    }

    /** Sent quotes lapsing within the window — the follow-up alert feed. */
    @Transactional(readOnly = true)
    public List<QuoteResponse> findExpiringWithin(int days) {
        LocalDate cutoff = LocalDate.now(clock).plusDays(days);
        return quoteRepository.findByStatusAndValidUntilLessThanEqual(QuoteStatus.SENT, cutoff).stream()
                .map(QuoteResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RateResponse> findRatesForLane(String originPortCode, String destinationPortCode, ShippingMode mode) {
        LocalDate today = LocalDate.now(clock);
        return laneRepository
                .findByOriginPortCodeAndDestinationPortCodeAndMode(originPortCode, destinationPortCode, mode)
                .map(lane -> rateRepository.findByLaneId(lane.getId()).stream()
                        .map(rate -> RateResponse.fromEntity(rate, today))
                        .toList())
                .orElseGet(List::of);
    }

    // ----------------------------------------------------------------- helpers

    private void runScreening(Quote quote, CreateQuoteRequest request, Instant now) {
        try {
            ScreeningResult shipper = screeningClient.screen(new DeniedPartyScreeningClient.ScreeningRequest(
                    request.shipperName(), request.shipperAddress(), request.shipperCountry(), EntityType.SHIPPER));
            if (!shipper.cleared()) {
                quote.recordScreeningFlagged(EntityType.SHIPPER, shipper.matchedList(),
                        shipper.matchScore(), shipper.referenceId(), now);
                return;
            }

            ScreeningResult consignee = screeningClient.screen(new DeniedPartyScreeningClient.ScreeningRequest(
                    request.consigneeName(), request.consigneeAddress(), request.consigneeCountry(), EntityType.CONSIGNEE));
            if (!consignee.cleared()) {
                quote.recordScreeningFlagged(EntityType.CONSIGNEE, consignee.matchedList(),
                        consignee.matchScore(), consignee.referenceId(), now);
                return;
            }

            quote.recordScreeningCleared(consignee.referenceId(), now);
        } catch (DeniedPartyScreeningClient.ScreeningServiceUnavailableException ex) {
            // Screening stays PENDING, which blocks send(). Failing open here would be
            // a compliance breach, so the quote is left deliberately stuck.
            log.warn("Denied party screening unavailable for quote {} — quote remains blocked: {}",
                    quote.getQuoteReference(), ex.getMessage());
        }
    }

    private void warnOnDuplicateOpenQuote(CreateQuoteRequest request) {
        List<Quote> existing = quoteRepository
                .findByCustomerIdAndOriginPortCodeAndDestinationPortCodeAndStatusIn(
                        request.customerId(),
                        request.originPortCode(),
                        request.destinationPortCode(),
                        List.of(QuoteStatus.DRAFT, QuoteStatus.SENT));
        if (!existing.isEmpty()) {
            log.info("Customer {} already has {} open quote(s) for {}->{} — consider reusing or superseding",
                    request.customerId(), existing.size(),
                    request.originPortCode(), request.destinationPortCode());
        }
    }

    private QuoteCargoDetail toCargoDetail(CargoDetailRequest request) {
        return QuoteCargoDetail.builder()
                .description(request.description())
                .hsCode(request.hsCode())
                .pieces(request.pieces())
                .weightKg(request.weightKg())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .hazmat(request.hazmat())
                .temperatureControlled(request.temperatureControlled())
                .oversized(request.oversized())
                .build();
    }

    private Quote getQuoteOrThrow(UUID quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
    }
}
