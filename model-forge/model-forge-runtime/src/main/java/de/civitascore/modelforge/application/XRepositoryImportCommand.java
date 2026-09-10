package de.civitascore.modelforge.application;

public record XRepositoryImportCommand(
    String identifier,
    String version,
    String urnScope,
    String urnOwner,
    Boolean importAsXsd,
    Boolean preserveUpstreamVersion
) {
    public String effectiveScope() {
        return urnScope != null && !urnScope.isBlank() ? urnScope : "standard";
    }

    public String effectiveOwner() {
        return urnOwner != null && !urnOwner.isBlank() ? urnOwner : "xoev";
    }

    public boolean effectiveImportAsXsd() {
        return Boolean.TRUE.equals(importAsXsd);
    }

    /**
     * When {@code true}, the XÖV/XRepository version ({@link #version}) is adopted verbatim as the
     * stored artifact version instead of Model Forge's usual version authority (1.0.0 for a new
     * artifact, otherwise the next SemVer bump). Opt-in and off by default, since it is a deliberate
     * departure from "Model Forge assigns every version" for the sake of preserving upstream
     * identity — accepted as given, with no check that it is "newer" than the current version.
     */
    public boolean effectivePreserveUpstreamVersion() {
        return Boolean.TRUE.equals(preserveUpstreamVersion);
    }

    public static void requireValid(XRepositoryImportCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("XRepository import command is required");
        }
        if (command.identifier() == null || command.identifier().isBlank()) {
            throw new IllegalArgumentException("XRepository identifier is required");
        }
        if (command.version() == null || command.version().isBlank()) {
            throw new IllegalArgumentException("XRepository version is required");
        }
    }
}
