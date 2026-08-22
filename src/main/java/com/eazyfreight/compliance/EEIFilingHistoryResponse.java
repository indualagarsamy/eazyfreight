package com.eazyfreight.compliance;

import java.time.Instant;
import java.util.UUID;

public record EEIFilingHistoryResponse(
        UUID id,
        int sequenceNumber,
        FilingStatus fromStatus,
        FilingStatus toStatus,
        Instant occurredAt,
        String actor,
        String detail
) {
    public static EEIFilingHistoryResponse fromEntity(EEIFilingHistory entry) {
        return new EEIFilingHistoryResponse(
                entry.getId(),
                entry.getSequenceNumber(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getOccurredAt(),
                entry.getActor(),
                entry.getDetail()
        );
    }
}
