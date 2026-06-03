// ── SmartHire · exception/ErrorResponse.java ──
package com.smarthire.exception;
import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(
    String code,
    String error,
    String requestId,
    OffsetDateTime timestamp,
    Map<String, String> fieldErrors
) {}
