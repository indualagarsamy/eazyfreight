package com.eazyfreight.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * An origin/destination/mode triple. Referenced by both Rate and Quote and owned by
 * neither — ports do not change, so a lane is immutable once created.
 */
@Entity
@Table(name = "lanes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Lane {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "origin_port_code", nullable = false, length = 8)
    private String originPortCode;

    @Column(name = "destination_port_code", nullable = false, length = 8)
    private String destinationPortCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private ShippingMode mode;
}
