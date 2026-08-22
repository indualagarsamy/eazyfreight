package com.eazyfreight.compliance;

import java.time.Instant;
import java.util.UUID;

public record ItnRecordResponse(
        UUID id,
        String itnNumber,
        Instant issuedAt,
        boolean active,
        boolean simulated,
        UUID supersededByItnId,
        Instant recordedAt,
        String recordedBy
) {
    public static ItnRecordResponse fromEntity(ItnRecord record) {
        return new ItnRecordResponse(
                record.getId(),
                record.getItnNumber(),
                record.getIssuedAt(),
                record.isActive(),
                record.toItnNumber().isSimulated(),
                record.getSupersededByItnId(),
                record.getRecordedAt(),
                record.getRecordedBy()
        );
    }
}
