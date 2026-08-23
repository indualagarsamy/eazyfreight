package com.eazyfreight.quote.controller;

import com.eazyfreight.quote.domain.Quote;
import com.eazyfreight.quote.domain.QuoteStatus;
import com.eazyfreight.quote.domain.ShippingMode;
import com.eazyfreight.quote.dto.AcceptQuoteRequest;
import com.eazyfreight.quote.dto.BuildQuotationRequest;
import com.eazyfreight.quote.dto.CreateQuoteRequest;
import com.eazyfreight.quote.dto.DeclineQuoteRequest;
import com.eazyfreight.quote.dto.QuoteResponse;
import com.eazyfreight.quote.dto.RateResponse;
import com.eazyfreight.quote.service.QuoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping
    public List<QuoteResponse> getAll() {
        return quoteService.findAll();
    }

    @GetMapping("/{id}")
    public QuoteResponse getById(@PathVariable UUID id) {
        return quoteService.findById(id);
    }

    @GetMapping("/by-reference/{quoteReference}")
    public QuoteResponse getByReference(@PathVariable String quoteReference) {
        return quoteService.findByReference(quoteReference);
    }

    @GetMapping("/open")
    public List<QuoteResponse> getOpen() {
        return quoteService.findOpen();
    }

    @GetMapping("/expiring")
    public List<QuoteResponse> getExpiring(@RequestParam(defaultValue = "3") int withinDays) {
        return quoteService.findExpiringWithin(withinDays);
    }

    @GetMapping("/by-customer/{customerId}")
    public List<QuoteResponse> getByCustomer(
            @PathVariable UUID customerId,
            @RequestParam(required = false) QuoteStatus status) {
        return quoteService.findByCustomer(customerId, status);
    }

    @GetMapping("/rates")
    public List<RateResponse> getRatesForLane(
            @RequestParam String originPortCode,
            @RequestParam String destinationPortCode,
            @RequestParam ShippingMode mode) {
        return quoteService.findRatesForLane(originPortCode, destinationPortCode, mode);
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        QuoteResponse response = quoteService.createQuoteRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/rescreen")
    public QuoteResponse rescreen(@PathVariable UUID id, @Valid @RequestBody CreateQuoteRequest request) {
        return quoteService.rescreen(id, request);
    }

    @PostMapping("/{id}/build")
    public QuoteResponse build(@PathVariable UUID id, @Valid @RequestBody BuildQuotationRequest request) {
        return quoteService.buildQuotation(id, request);
    }

    @PostMapping("/{id}/send")
    public QuoteResponse send(@PathVariable UUID id) {
        return quoteService.send(id);
    }

    @PostMapping("/{id}/accept")
    public QuoteResponse accept(@PathVariable UUID id, @Valid @RequestBody AcceptQuoteRequest request) {
        return quoteService.accept(id, request);
    }

    @PostMapping("/{id}/decline")
    public QuoteResponse decline(@PathVariable UUID id, @RequestBody(required = false) DeclineQuoteRequest request) {
        return quoteService.decline(id, request);
    }

    @PostMapping("/{id}/expire")
    public QuoteResponse expire(@PathVariable UUID id) {
        return quoteService.expire(id);
    }
}
