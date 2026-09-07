package com.eazyfreight.quote.controller;

import com.eazyfreight.quote.dto.AcceptQuoteRequest;
import com.eazyfreight.quote.dto.BuildQuotationRequest;
import com.eazyfreight.quote.dto.CargoDetailRequest;
import com.eazyfreight.quote.dto.CargoDetailResponse;
import com.eazyfreight.quote.dto.CreateQuoteRequest;
import com.eazyfreight.quote.dto.DeclineQuoteRequest;
import com.eazyfreight.quote.dto.QuoteLineRequest;
import com.eazyfreight.quote.dto.QuoteLineResponse;
import com.eazyfreight.quote.dto.QuoteResponse;
import com.eazyfreight.quote.dto.RateResponse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Converts between Quote domain DTOs and the generated OpenAPI models. */
final class QuoteApiMapper {

    private QuoteApiMapper() {
    }

    // -------------------------------------------------------------- responses

    static com.eazyfreight.quote.model.QuoteResponse toModel(QuoteResponse quote) {
        com.eazyfreight.quote.model.QuoteResponse model = new com.eazyfreight.quote.model.QuoteResponse();
        model.setId(quote.id());
        model.setQuoteReference(quote.quoteReference());
        model.setCustomerId(quote.customerId());
        model.setStatus(mapEnum(quote.status(), com.eazyfreight.quote.model.QuoteStatus.class));
        model.setShippingMode(mapEnum(quote.shippingMode(), com.eazyfreight.quote.model.ShippingMode.class));
        model.setOriginPortCode(quote.originPortCode());
        model.setDestinationPortCode(quote.destinationPortCode());
        model.setIncoterms(quote.incoterms());
        model.setSelectedCarrierId(quote.selectedCarrierId());
        model.setScreeningStatus(mapEnum(quote.screeningStatus(), com.eazyfreight.quote.model.ScreeningStatus.class));
        model.setScreeningReferenceId(quote.screeningReferenceId());
        model.setRequestedEtd(quote.requestedEtd());
        model.setValidFrom(quote.validFrom());
        model.setValidUntil(quote.validUntil());
        model.setRateValidUntil(quote.rateValidUntil());
        model.setCreatedAt(toOffsetDateTime(quote.createdAt()));
        model.setSentAt(toOffsetDateTime(quote.sentAt()));
        model.setAcceptedAt(toOffsetDateTime(quote.acceptedAt()));
        model.setDeclinedAt(toOffsetDateTime(quote.declinedAt()));
        model.setExpiredAt(toOffsetDateTime(quote.expiredAt()));
        model.setDeclineReason(quote.declineReason());
        model.setNotes(quote.notes());
        model.setTotalBuyRate(quote.totalBuyRate());
        model.setTotalSellRate(quote.totalSellRate());
        model.setMargin(quote.margin());
        model.setCurrency(quote.currency());
        model.setSpotRate(quote.spotRate());
        model.setSpecialHandling(quote.specialHandling());
        model.setInsuranceRequired(quote.insuranceRequired());
        model.setQuoteLines(quote.quoteLines().stream().map(QuoteApiMapper::toModel).toList());
        model.setCargoDetails(quote.cargoDetails().stream().map(QuoteApiMapper::toModel).toList());
        return model;
    }

    private static com.eazyfreight.quote.model.QuoteLineResponse toModel(QuoteLineResponse line) {
        com.eazyfreight.quote.model.QuoteLineResponse model = new com.eazyfreight.quote.model.QuoteLineResponse();
        model.setId(line.id());
        model.setLineType(mapEnum(line.lineType(), com.eazyfreight.quote.model.QuoteLineType.class));
        model.setDescription(line.description());
        model.setBuyRate(line.buyRate());
        model.setSellRate(line.sellRate());
        model.setCurrency(line.currency());
        model.setQuantity(line.quantity());
        model.setUnit(mapEnum(line.unit(), com.eazyfreight.quote.model.ChargeUnit.class));
        model.setAmount(line.amount());
        return model;
    }

    private static com.eazyfreight.quote.model.CargoDetailResponse toModel(CargoDetailResponse detail) {
        com.eazyfreight.quote.model.CargoDetailResponse model = new com.eazyfreight.quote.model.CargoDetailResponse();
        model.setId(detail.id());
        model.setDescription(detail.description());
        model.setHsCode(detail.hsCode());
        model.setPieces(detail.pieces());
        model.setWeightKg(detail.weightKg());
        model.setLengthCm(detail.lengthCm());
        model.setWidthCm(detail.widthCm());
        model.setHeightCm(detail.heightCm());
        model.setVolumetricWeightCbm(detail.volumetricWeightCbm());
        model.setVolumetricWeightKg(detail.volumetricWeightKg());
        model.setChargeableWeight(detail.chargeableWeight());
        model.setChargeableUnit(mapEnum(detail.chargeableUnit(), com.eazyfreight.quote.model.ChargeUnit.class));
        model.setHazmat(detail.hazmat());
        model.setTemperatureControlled(detail.temperatureControlled());
        model.setOversized(detail.oversized());
        return model;
    }

    static com.eazyfreight.quote.model.RateResponse toModel(RateResponse rate) {
        com.eazyfreight.quote.model.RateResponse model = new com.eazyfreight.quote.model.RateResponse();
        model.setId(rate.id());
        model.setLaneId(rate.laneId());
        model.setCarrierId(rate.carrierId());
        model.setBuyRate(rate.buyRate());
        model.setCurrency(rate.currency());
        model.setRateType(mapEnum(rate.rateType(), com.eazyfreight.quote.model.RateType.class));
        model.setUnit(mapEnum(rate.unit(), com.eazyfreight.quote.model.ChargeUnit.class));
        model.setTransitDays(rate.transitDays());
        model.setValidFrom(rate.validFrom());
        model.setValidUntil(rate.validUntil());
        model.setExpired(rate.expired());
        model.setSurcharges(rate.surcharges().stream().map(QuoteApiMapper::toModel).toList());
        return model;
    }

    private static com.eazyfreight.quote.model.SurchargeResponse toModel(RateResponse.SurchargeResponse surcharge) {
        com.eazyfreight.quote.model.SurchargeResponse model = new com.eazyfreight.quote.model.SurchargeResponse();
        model.setId(surcharge.id());
        model.setSurchargeType(mapEnum(surcharge.surchargeType(), com.eazyfreight.quote.model.SurchargeType.class));
        model.setAmount(surcharge.amount());
        model.setCurrency(surcharge.currency());
        model.setValidFrom(surcharge.validFrom());
        model.setValidUntil(surcharge.validUntil());
        model.setExpired(surcharge.expired());
        return model;
    }

    // --------------------------------------------------------------- requests

    static CreateQuoteRequest toDomain(com.eazyfreight.quote.model.CreateQuoteRequest request) {
        List<CargoDetailRequest> cargoDetails = request.getCargoDetails() == null
                ? List.of()
                : request.getCargoDetails().stream().map(QuoteApiMapper::toDomain).toList();
        return new CreateQuoteRequest(
                request.getCustomerId(),
                mapEnum(request.getShippingMode(), com.eazyfreight.quote.domain.ShippingMode.class),
                request.getOriginPortCode(),
                request.getDestinationPortCode(),
                request.getIncoterms(),
                cargoDetails,
                request.getRequestedEtd(),
                request.getSpecialHandling(),
                Boolean.TRUE.equals(request.getInsuranceRequired()),
                request.getCurrency(),
                request.getShipperName(),
                request.getShipperAddress(),
                request.getShipperCountry(),
                request.getConsigneeName(),
                request.getConsigneeAddress(),
                request.getConsigneeCountry());
    }

    private static CargoDetailRequest toDomain(com.eazyfreight.quote.model.CargoDetailRequest request) {
        return new CargoDetailRequest(
                request.getDescription(),
                request.getHsCode(),
                request.getPieces(),
                request.getWeightKg(),
                request.getLengthCm(),
                request.getWidthCm(),
                request.getHeightCm(),
                Boolean.TRUE.equals(request.getHazmat()),
                Boolean.TRUE.equals(request.getTemperatureControlled()),
                Boolean.TRUE.equals(request.getOversized()));
    }

    static BuildQuotationRequest toDomain(com.eazyfreight.quote.model.BuildQuotationRequest request) {
        List<QuoteLineRequest> lines = request.getLines() == null
                ? List.of()
                : request.getLines().stream().map(QuoteApiMapper::toDomain).toList();
        return new BuildQuotationRequest(
                lines,
                request.getValidityDays(),
                request.getRateValidUntil(),
                Boolean.TRUE.equals(request.getSpotRate()),
                request.getNotes());
    }

    private static QuoteLineRequest toDomain(com.eazyfreight.quote.model.QuoteLineRequest request) {
        return new QuoteLineRequest(
                mapEnum(request.getLineType(), com.eazyfreight.quote.domain.QuoteLineType.class),
                request.getDescription(),
                request.getBuyRate(),
                request.getSellRate(),
                request.getQuantity(),
                mapEnum(request.getUnit(), com.eazyfreight.quote.domain.ChargeUnit.class));
    }

    static AcceptQuoteRequest toDomain(com.eazyfreight.quote.model.AcceptQuoteRequest request) {
        return new AcceptQuoteRequest(request.getSelectedCarrierId());
    }

    static DeclineQuoteRequest toDomain(com.eazyfreight.quote.model.DeclineQuoteRequest request) {
        return new DeclineQuoteRequest(request == null ? null : request.getReason());
    }

    static com.eazyfreight.quote.domain.ShippingMode toDomain(com.eazyfreight.quote.model.ShippingMode mode) {
        return mapEnum(mode, com.eazyfreight.quote.domain.ShippingMode.class);
    }

    static com.eazyfreight.quote.domain.QuoteStatus toDomain(com.eazyfreight.quote.model.QuoteStatus status) {
        return mapEnum(status, com.eazyfreight.quote.domain.QuoteStatus.class);
    }

    // ----------------------------------------------------------------- shared

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        return source == null ? null : Enum.valueOf(targetType, source.name());
    }
}
