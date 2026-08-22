package com.eazyfreight.booking;

import java.time.Instant;
import java.util.UUID;

public record BookingStatusHistoryResponse(
        UUID id,
        int sequenceNumber,
        BookingStatus fromStatus,
        BookingStatus toStatus,
        Instant changedAt,
        String changedBy,
        String reason,
        StatusChangeSource source
) {
    public static BookingStatusHistoryResponse fromEntity(BookingStatusHistory entry) {
        return new BookingStatusHistoryResponse(
                entry.getId(),
                entry.getSequenceNumber(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt(),
                entry.getChangedBy(),
                entry.getReason(),
                entry.getSource()
        );
    }
}
