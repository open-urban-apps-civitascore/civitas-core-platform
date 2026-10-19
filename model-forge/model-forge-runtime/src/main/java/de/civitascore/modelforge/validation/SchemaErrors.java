package de.civitascore.modelforge.validation;

import com.networknt.schema.ValidationMessage;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;

/**
 * Shared mapper from a networknt validation message to a public diagnostic DTO.
 */
public final class SchemaErrors {

    private SchemaErrors() {
    }

    public static Diagnostic toDiagnostic(ValidationMessage error, String fallbackCode) {
        String code = error.getMessageKey() != null ? error.getMessageKey() : error.getCode();
        String message = error.getMessage();
        return new Diagnostic(
            DiagnosticSeverity.ERROR,
            message == null || message.isBlank() ? "Schema validation failed" : message,
            code == null || code.isBlank() ? fallbackCode : code,
            String.valueOf(error.getInstanceLocation())
        );
    }
}
