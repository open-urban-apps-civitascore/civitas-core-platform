package de.civitascore.modelforge.contract;

/**
 * Raised when the backing artifact registry cannot be reached or used.
 */
public class RegistryUnavailableException extends ModelForgeException {

    public RegistryUnavailableException(String message) {
        super(message);
    }

    public RegistryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
