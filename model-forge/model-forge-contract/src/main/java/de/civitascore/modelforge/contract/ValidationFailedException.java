package de.civitascore.modelforge.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raised when a requested operation cannot continue because validation failed.
 */
public class ValidationFailedException extends ModelForgeException {

    private final List<Diagnostic> diagnostics;

    public ValidationFailedException(String message, List<Diagnostic> diagnostics) {
        super(message);
        this.diagnostics =
            diagnostics == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }
}
