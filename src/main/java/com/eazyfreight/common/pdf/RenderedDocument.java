package com.eazyfreight.common.pdf;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A rendered document on its way out of the application.
 *
 * <p>Carries its own filename because that is what the browser saves the file as, and a
 * download called {@code pdf} or {@code 7f3a-…} is one the recipient has to open to
 * identify. The name comes from the aggregate — {@code HBL-2026-00001-rev0.pdf} — so a
 * folder of them sorts and reads correctly.
 */
public record RenderedDocument(String fileName, byte[] content) {

    public ResponseEntity<Resource> asAttachment() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                // The filename lives in Content-Disposition, which a browser fetch cannot
                // read cross-origin unless it is exposed. The UI reads it to name the
                // saved file rather than reconstructing it and getting it subtly wrong.
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(new ByteArrayResource(content));
    }
}
