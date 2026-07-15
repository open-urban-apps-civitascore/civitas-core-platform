package de.civitascore.modelforge.persistence.postgres;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Null-safe {@link Instant} ↔ {@link OffsetDateTime} conversions at UTC, shared by the registry
 * repositories. {@code timestamptz} columns are bound as UTC {@code OffsetDateTime} on write and
 * read back as {@code Instant}.
 */
final class RegistryTime {

    private RegistryTime() {
    }

    /** {@code Instant} → UTC {@code OffsetDateTime} ({@code null} → {@code null}). */
    static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** {@code OffsetDateTime} → {@code Instant} ({@code null} → {@code null}). */
    static Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
