package com.eazyfreight.quote;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Quote aggregate root.
 *
 * <p>Every state transition runs through a command method on this class. There are
 * no public setters: the only way to move a quote from DRAFT to SENT is to call
 * {@link #send()}, which refuses unless screening has cleared, at least one line
 * has been priced, and the quote has not already expired.
 *
 * <p>Buy and sell totals are both retained, so margin is a property of the record
 * rather than something reconstructed later by hand.
 */
@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote extends AbstractAggregateRoot<Quote> implements Persistable<UUID> {

    /**
     * Assigned in the factory rather than by the database, so that the creation
     * event can carry the identity of the thing that was created.
     */
    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "quote_reference", nullable = false, unique = true, length = 32)
    private String quoteReference;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private QuoteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_mode", nullable = false, length = 16)
    private ShippingMode shippingMode;

    @Column(name = "origin_port_code", nullable = false, length = 8)
    private String originPortCode;

    @Column(name = "destination_port_code", nullable = false, length = 8)
    private String destinationPortCode;

    @Column(name = "incoterms", nullable = false, length = 8)
    private String incoterms;

    @Column(name = "selected_carrier_id")
    private UUID selectedCarrierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "screening_status", nullable = false, length = 16)
    private ScreeningStatus screeningStatus;

    @Column(name = "screening_reference_id", length = 64)
    private String screeningReferenceId;

    @Column(name = "requested_etd")
    private LocalDate requestedEtd;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    /**
     * Earliest expiry among the carrier rates this quote was priced from, captured
     * at build time. Held here so acceptance can be refused without reaching into
     * the Rate aggregate — see {@link #accept}.
     */
    @Column(name = "rate_valid_until")
    private LocalDate rateValidUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "total_buy_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBuyRate = BigDecimal.ZERO;

    @Column(name = "total_sell_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSellRate = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "is_spot_rate", nullable = false)
    private boolean spotRate;

    @Column(name = "special_handling")
    private String specialHandling;

    @Column(name = "insurance_required", nullable = false)
    private boolean insuranceRequired;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuoteLine> quoteLines = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuoteCargoDetail> cargoDetails = new ArrayList<>();

    // ---------------------------------------------------------------- creation

    /**
     * Logs a customer inquiry. The quote starts in DRAFT with screening PENDING —
     * no pricing work is permitted until screening clears.
     */
    public static Quote request(
            String quoteReference,
            UUID customerId,
            ShippingMode shippingMode,
            String originPortCode,
            String destinationPortCode,
            String incoterms,
            LocalDate requestedEtd,
            String specialHandling,
            boolean insuranceRequired,
            String currency,
            Instant now
    ) {
        if (originPortCode.equalsIgnoreCase(destinationPortCode)) {
            throw new DomainRuleViolationException(
                    "Origin and destination port must differ (both were " + originPortCode + ")");
        }

        Quote quote = new Quote();
        quote.id = UUID.randomUUID();
        quote.quoteReference = quoteReference;
        quote.customerId = customerId;
        quote.status = QuoteStatus.DRAFT;
        quote.shippingMode = shippingMode;
        quote.originPortCode = originPortCode;
        quote.destinationPortCode = destinationPortCode;
        quote.incoterms = incoterms;
        quote.screeningStatus = ScreeningStatus.PENDING;
        quote.requestedEtd = requestedEtd;
        quote.specialHandling = specialHandling;
        quote.insuranceRequired = insuranceRequired;
        quote.currency = currency;
        quote.createdAt = now;

        quote.registerEvent(new QuoteEvent.QuoteRequestReceived(
                quote.id, quoteReference, customerId, shippingMode,
                originPortCode, destinationPortCode, now));
        return quote;
    }

    /** Attaches a cargo line and derives its chargeable weight for this quote's mode. */
    public void addCargoDetail(QuoteCargoDetail detail) {
        requireStatus(QuoteStatus.DRAFT, "Cargo details can only be added while the quote is DRAFT");
        detail.assignTo(this);
        detail.computeChargeableWeight(shippingMode);
        cargoDetails.add(detail);
    }

    // --------------------------------------------------------------- screening

    /** Records a clear result from denied party screening. Pricing may now proceed. */
    public void recordScreeningCleared(String screeningReferenceId, Instant now) {
        this.screeningStatus = ScreeningStatus.CLEARED;
        this.screeningReferenceId = screeningReferenceId;
        registerEvent(new QuoteEvent.DeniedPartyScreeningCleared(id, screeningReferenceId, now));
    }

    /**
     * Records a denied party match. The quote is halted — it can never be sent, and
     * the customer is not told why.
     */
    public void recordScreeningFlagged(
            EntityType entityType,
            String matchedList,
            BigDecimal matchScore,
            String screeningReferenceId,
            Instant now
    ) {
        this.screeningStatus = ScreeningStatus.FLAGGED;
        this.screeningReferenceId = screeningReferenceId;
        registerEvent(new QuoteEvent.DeniedPartyScreeningFlagged(
                id, entityType, matchedList, matchScore, screeningReferenceId, now));
    }

    // ----------------------------------------------------------------- pricing

    /**
     * Prices the quotation. Replaces any previously built lines, so re-rating an
     * unsent quote is a single call rather than an edit-in-place.
     */
    public void build(
            List<QuoteLine> lines,
            LocalDate validFrom,
            LocalDate validUntil,
            LocalDate rateValidUntil,
            boolean spotRate,
            String notes,
            Instant now
    ) {
        requireStatus(QuoteStatus.DRAFT, "Only a DRAFT quote can be priced");
        if (lines.isEmpty()) {
            throw new DomainRuleViolationException("A quotation must have at least one quote line");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new DomainRuleViolationException("Quote validity must end after it begins");
        }

        quoteLines.clear();
        lines.forEach(line -> {
            line.assignTo(this);
            quoteLines.add(line);
        });

        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.rateValidUntil = rateValidUntil;
        this.spotRate = spotRate;
        this.notes = notes;
        this.totalBuyRate = quoteLines.stream()
                .map(QuoteLine::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalSellRate = quoteLines.stream()
                .map(QuoteLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        registerEvent(new QuoteEvent.QuotationBuilt(
                id, quoteReference, totalBuyRate, totalSellRate, currency, now));
    }

    // --------------------------------------------------------------- lifecycle

    /**
     * Sends the quotation to the customer. Refuses unless screening has cleared —
     * the compliance gate the monolith leaves to staff discretion.
     */
    public void send(LocalDate today, Instant now) {
        requireStatus(QuoteStatus.DRAFT, "Only a DRAFT quote can be sent");
        if (screeningStatus != ScreeningStatus.CLEARED) {
            throw new DomainRuleViolationException(
                    "Quote cannot be sent until denied party screening has cleared (currently " + screeningStatus + ")");
        }
        if (quoteLines.isEmpty()) {
            throw new DomainRuleViolationException("Quote cannot be sent without at least one quote line");
        }
        if (validUntil == null || !validUntil.isAfter(today)) {
            throw new DomainRuleViolationException("Quote cannot be sent once its validity period has elapsed");
        }

        this.status = QuoteStatus.SENT;
        this.sentAt = now;
        registerEvent(new QuoteEvent.QuoteSentToCustomer(id, quoteReference, customerId, validUntil, now));
    }

    /**
     * Records customer acceptance against a chosen carrier.
     *
     * <p>Acceptance is refused if the quote has lapsed, and separately if the
     * carrier rate underneath it has lapsed — the second case raises
     * {@code QuoteRerateRequired} so operations knows a re-rate, not a chase, is
     * what is needed.
     */
    public void accept(UUID selectedCarrierId, LocalDate today, Instant now) {
        requireStatus(QuoteStatus.SENT, "Only a SENT quote can be accepted");
        if (validUntil.isBefore(today)) {
            throw new DomainRuleViolationException(
                    "Quote " + quoteReference + " expired on " + validUntil + " and must be re-rated before acceptance");
        }
        if (rateValidUntil != null && rateValidUntil.isBefore(today)) {
            registerEvent(new QuoteEvent.QuoteRerateRequired(id, quoteReference, rateValidUntil, now));
            throw new DomainRuleViolationException(
                    "Underlying carrier rate expired on " + rateValidUntil + " — quote must be re-rated before acceptance");
        }

        this.status = QuoteStatus.ACCEPTED;
        this.selectedCarrierId = selectedCarrierId;
        this.acceptedAt = now;
        registerEvent(new QuoteEvent.QuoteAccepted(
                id, quoteReference, customerId, selectedCarrierId, shippingMode,
                totalBuyRate, totalSellRate, currency, now));
    }

    public void decline(String reason, Instant now) {
        requireStatus(QuoteStatus.SENT, "Only a SENT quote can be declined");
        this.status = QuoteStatus.DECLINED;
        this.declineReason = reason;
        this.declinedAt = now;
        registerEvent(new QuoteEvent.QuoteDeclined(id, quoteReference, customerId, reason, now));
    }

    /** Lapses a quote whose validity has run out with no customer response. */
    public void expire(Instant now) {
        if (status != QuoteStatus.DRAFT && status != QuoteStatus.SENT) {
            throw new DomainRuleViolationException("Only an open quote can expire (status was " + status + ")");
        }
        this.status = QuoteStatus.EXPIRED;
        this.expiredAt = now;
        registerEvent(new QuoteEvent.QuoteExpired(id, quoteReference, customerId, "CustomerNoResponse", now));
    }

    /** Returns an expired or lapsed quote to DRAFT so it can be priced again. */
    public void reopenForRerate() {
        if (status == QuoteStatus.ACCEPTED) {
            throw new DomainRuleViolationException("An accepted quote cannot be re-rated");
        }
        this.status = QuoteStatus.DRAFT;
        this.sentAt = null;
        this.expiredAt = null;
        this.declinedAt = null;
        this.declineReason = null;
    }

    // ----------------------------------------------------------------- queries

    public List<QuoteLine> getQuoteLines() {
        return Collections.unmodifiableList(quoteLines);
    }

    public List<QuoteCargoDetail> getCargoDetails() {
        return Collections.unmodifiableList(cargoDetails);
    }

    /** Sell less buy — visible per quote rather than only at month-end. */
    public BigDecimal margin() {
        return totalSellRate.subtract(totalBuyRate);
    }

    public boolean isExpiredAsOf(LocalDate today) {
        return validUntil != null && validUntil.isBefore(today);
    }

    /**
     * Events registered on this aggregate but not yet published. Spring Data reads
     * these on save; exposed here so the aggregate can be tested without a context.
     */
    public java.util.Collection<Object> pendingEvents() {
        return domainEvents();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    private void requireStatus(QuoteStatus expected, String message) {
        if (status != expected) {
            throw new DomainRuleViolationException(message + " (status was " + status + ")");
        }
    }
}
