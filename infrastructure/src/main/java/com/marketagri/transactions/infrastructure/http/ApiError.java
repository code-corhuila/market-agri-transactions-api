package com.marketagri.transactions.infrastructure.http;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId) {
}
