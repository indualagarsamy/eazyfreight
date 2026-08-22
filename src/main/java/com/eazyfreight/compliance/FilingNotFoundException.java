package com.eazyfreight.compliance;

import java.util.UUID;

public class FilingNotFoundException extends RuntimeException {

    public FilingNotFoundException(UUID id) {
        super("EEI filing not found with id: " + id);
    }

    public FilingNotFoundException(String filingReference) {
        super("EEI filing not found with reference: " + filingReference);
    }
}
