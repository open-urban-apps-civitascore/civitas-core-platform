package de.civitascore.modelforge.application;

/**
 * Thrown when a call to an external upstream service fails (the registry, XRepository, …).
 *
 * <p>Mapped to HTTP 502 Bad Gateway by the API exception handler — the upstream
 * system was unreachable or returned an error. Clients can safely retry.
 *
 * <p>Lives in the integration layer so that integration components can raise it
 * directly; the api layer (which may depend on integration) handles it.
 */
public class UpstreamException extends RuntimeException {

    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
