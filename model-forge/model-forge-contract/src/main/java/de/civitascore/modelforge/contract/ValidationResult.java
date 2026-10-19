package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/**
 * Validation result independent of any HTTP response shape.
 */
public record ValidationResult(boolean valid, List<Diagnostic> diagnostics) {

    public ValidationResult {
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static ValidationResult of(List<Diagnostic> diagnostics) {
        return new ValidationResult(
            diagnostics.stream().noneMatch(diagnostic -> DiagnosticSeverity.ERROR == diagnostic.severity()),
            diagnostics
        );
    }
}
