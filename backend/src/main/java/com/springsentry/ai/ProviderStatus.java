package com.springsentry.ai;

import java.time.Instant;

public record ProviderStatus(String mode, String message, Instant checkedAt) {
}
