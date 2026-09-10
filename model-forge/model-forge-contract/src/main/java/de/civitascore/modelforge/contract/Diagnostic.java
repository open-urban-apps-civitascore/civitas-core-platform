package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * A validation or processing diagnostic independent of any transport shape.
 */
public record Diagnostic(DiagnosticSeverity severity, String message, String code, String path) {

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(code, "code");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
