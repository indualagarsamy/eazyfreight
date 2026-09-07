package com.eazyfreight.quote.controller;

import com.eazyfreight.quote.api.QuoteApi;
import com.eazyfreight.quote.model.AcceptQuoteRequest;
import com.eazyfreight.quote.model.BuildQuotationRequest;
import com.eazyfreight.quote.model.CreateQuoteRequest;
import com.eazyfreight.quote.model.DeclineQuoteRequest;
import com.eazyfreight.quote.model.QuoteResponse;
import com.eazyfreight.quote.model.QuoteStatus;
import com.eazyfreight.quote.model.RateResponse;
import com.eazyfreight.quote.model.ShippingMode;
import com.eazyfreight.quote.service.QuoteService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Quote endpoints.
 *
 * <p>Lifecycle changes are POSTs to named sub-resources — {@code /send},
 * {@code /accept}, {@code /decline} — rather than a PUT that lets a caller set
 * status to anything. The transitions the domain permits are the transitions the
 * API exposes.
 */
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController implements QuoteApi {

    private final QuoteService quoteService;

    @Override
    @RequestMapping(method = RequestMethod.GET, value = {"", "/"}, produces = "application/json")
    public ResponseEntity<List<QuoteResponse>> getAllQuotes() {
        return ResponseEntity.ok(quoteService.findAll().stream().map(QuoteApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<QuoteResponse> getQuoteById(UUID id) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(quoteService.findById(id)));
    }

    @Override
    public ResponseEntity<QuoteResponse> getQuoteByReference(String quoteReference) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(quoteService.findByReference(quoteReference)));
    }

    @Override
    public ResponseEntity<List<QuoteResponse>> getOpenQuotes() {
        return ResponseEntity.ok(quoteService.findOpen().stream().map(QuoteApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<QuoteResponse>> getExpiringQuotes(Integer withinDays) {
        return ResponseEntity.ok(quoteService.findExpiringWithin(withinDays).stream()
                .map(QuoteApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<QuoteResponse>> getQuotesByCustomer(UUID customerId, QuoteStatus status) {
        return ResponseEntity.ok(quoteService.findByCustomer(customerId, QuoteApiMapper.toDomain(status)).stream()
                .map(QuoteApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<RateResponse>> getRatesForLane(String originPortCode, String destinationPortCode, ShippingMode mode) {
        return ResponseEntity.ok(quoteService.findRatesForLane(
                        originPortCode, destinationPortCode, QuoteApiMapper.toDomain(mode)).stream()
                .map(QuoteApiMapper::toModel).toList());
    }

    @Override
    @RequestMapping(method = RequestMethod.POST, value = {"", "/"}, consumes = "application/json", produces = "application/json")
    public ResponseEntity<QuoteResponse> createQuote(CreateQuoteRequest createQuoteRequest) {
        var response = quoteService.createQuoteRequest(QuoteApiMapper.toDomain(createQuoteRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(QuoteApiMapper.toModel(response));
    }

    @Override
    public ResponseEntity<QuoteResponse> rescreenQuote(UUID id, CreateQuoteRequest createQuoteRequest) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(
                quoteService.rescreen(id, QuoteApiMapper.toDomain(createQuoteRequest))));
    }

    @Override
    public ResponseEntity<QuoteResponse> buildQuote(UUID id, BuildQuotationRequest buildQuotationRequest) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(
                quoteService.buildQuotation(id, QuoteApiMapper.toDomain(buildQuotationRequest))));
    }

    @Override
    public ResponseEntity<QuoteResponse> sendQuote(UUID id) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(quoteService.send(id)));
    }

    @Override
    public ResponseEntity<QuoteResponse> acceptQuote(UUID id, AcceptQuoteRequest acceptQuoteRequest) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(
                quoteService.accept(id, QuoteApiMapper.toDomain(acceptQuoteRequest))));
    }

    @Override
    public ResponseEntity<QuoteResponse> declineQuote(UUID id, DeclineQuoteRequest declineQuoteRequest) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(
                quoteService.decline(id, QuoteApiMapper.toDomain(declineQuoteRequest))));
    }

    @Override
    public ResponseEntity<QuoteResponse> expireQuote(UUID id) {
        return ResponseEntity.ok(QuoteApiMapper.toModel(quoteService.expire(id)));
    }
}
