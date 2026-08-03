package dev.forme.operations.analytics;

import java.time.Instant;

public record BenchmarkSeedResponse(int sampleRows, int distinctSkus, Instant generatedAt) { }

