package com.eazyfreight.documentation;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The three negotiable originals, when the release type calls for them.
 *
 * <p>Whoever holds an original controls the cargo, so the count issued and the
 * count surrendered both matter: a BOL cannot be amended while originals are still
 * out in the world. In the monolith the courier reference lives in an email, if
 * anywhere.
 */
@Entity
@Table(name = "original_bol_tracking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OriginalBOLTracking {

    /** A full set is three originals. */
    public static final int FULL_SET = 3;

    @Id
    private UUID id;

    @Column(name = "house_bol_id", nullable = false)
    private UUID houseBolId;

    @Column(name = "originals_issued", nullable = false)
    private int originalsIssued;

    @Column(name = "originals_surrendered", nullable = false)
    private int originalsSurrendered;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_to", length = 128)
    private String releasedTo;

    @Column(name = "courier_reference", length = 64)
    private String courierReference;

    @Column(name = "surrendered_at")
    private Instant surrenderedAt;

    static OriginalBOLTracking issue(
            UUID houseBolId, String releasedTo, String courierReference, Instant now) {
        OriginalBOLTracking tracking = new OriginalBOLTracking();
        tracking.id = UUID.randomUUID();
        tracking.houseBolId = houseBolId;
        tracking.originalsIssued = FULL_SET;
        tracking.originalsSurrendered = 0;
        tracking.releasedAt = now;
        tracking.releasedTo = releasedTo;
        tracking.courierReference = courierReference;
        return tracking;
    }

    void recordSurrendered(int count, Instant now) {
        if (count < 1) {
            throw new DomainRuleViolationException("Surrendered count must be at least 1");
        }
        if (originalsSurrendered + count > originalsIssued) {
            throw new DomainRuleViolationException(
                    "Cannot surrender %d originals — only %d of %d are outstanding"
                            .formatted(count, originalsIssued - originalsSurrendered, originalsIssued));
        }
        this.originalsSurrendered += count;
        if (allSurrendered()) {
            this.surrenderedAt = now;
        }
    }

    public boolean allSurrendered() {
        return originalsSurrendered >= originalsIssued;
    }

    public int outstanding() {
        return originalsIssued - originalsSurrendered;
    }
}
