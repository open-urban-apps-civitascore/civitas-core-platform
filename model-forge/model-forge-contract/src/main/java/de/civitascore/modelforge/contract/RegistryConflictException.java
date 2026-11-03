package de.civitascore.modelforge.contract;

/**
 * Raised when a write conflicts with data the registry already holds, such as a version that
 * exists already.
 */
public class RegistryConflictException extends ModelForgeException {

    public RegistryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
