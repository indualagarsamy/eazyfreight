package com.eazyfreight.documentation.domain;

import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionRecipient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of who was sent the document, when, and which revision they got.
 *
 * <p>Append-only. The monolith has a single {@code BOLSent} bit, which cannot
 * answer whether the consignee agent ever received Rev 1 after an amendment.
 */
@Entity
@Table(name = "house_bol_distributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseBOLDistribution {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "house_bol_id", nullable = false)
    private HouseBOL houseBol;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient", nullable = false, length = 24)
    private DistributionRecipient recipient;

    @Column(name = "recipient_name", length = 128)
    private String recipientName;

    @Column(name = "recipient_address", length = 255)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private DistributionChannel channel;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "sent_by", nullable = false, length = 64)
    private String sentBy;

    @Column(name = "reference", length = 64)
    private String reference;

    static HouseBOLDistribution record(
            HouseBOL houseBol, DistributionRecipient recipient, String recipientName,
            String recipientAddress, DistributionChannel channel, int revisionNumber,
            String reference, Instant now, String actor) {
        HouseBOLDistribution distribution = new HouseBOLDistribution();
        distribution.id = UUID.randomUUID();
        distribution.houseBol = houseBol;
        distribution.recipient = recipient;
        distribution.recipientName = recipientName;
        distribution.recipientAddress = recipientAddress;
        distribution.channel = channel;
        distribution.revisionNumber = revisionNumber;
        distribution.reference = reference;
        distribution.sentAt = now;
        distribution.sentBy = actor;
        return distribution;
    }
}
