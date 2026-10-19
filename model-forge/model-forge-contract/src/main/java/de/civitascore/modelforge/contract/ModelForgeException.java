package de.civitascore.modelforge.contract;

/**
 * Base exception for semantic Model Forge failures.
 */
public class ModelForgeException extends RuntimeException {

    public ModelForgeException(String message) {
        super(message);
    }

    public ModelForgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
