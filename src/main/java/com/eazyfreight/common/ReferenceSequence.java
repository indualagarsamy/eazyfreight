package com.eazyfreight.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Per-prefix, per-year counter backing human-readable business references such as
 * {@code Q-2024-00142} and {@code EF-2024-00287}.
 *
 * <p>Held in the database and taken under a write lock so two concurrent quote
 * requests cannot be handed the same reference.
 */
@Entity
@Table(name = "reference_sequences")
@IdClass(ReferenceSequence.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceSequence {

    @Id
    @Column(name = "prefix", nullable = false, length = 8)
    private String prefix;

    @Id
    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    ReferenceSequence(String prefix, int year) {
        this.prefix = prefix;
        this.sequenceYear = year;
        this.lastValue = 0L;
    }

    long next() {
        return ++lastValue;
    }

    @Getter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String prefix;
        private int sequenceYear;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return sequenceYear == key.sequenceYear && prefix.equals(key.prefix);
        }

        @Override
        public int hashCode() {
            return prefix.hashCode() * 31 + sequenceYear;
        }
    }
}
