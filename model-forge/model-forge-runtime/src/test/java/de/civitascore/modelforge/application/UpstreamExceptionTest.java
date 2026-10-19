package de.civitascore.modelforge.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamExceptionTest {

    @Test
    void messageConstructor_setsMessageAndNullCause() {
        var ex = new UpstreamException("registry is down");
        assertThat(ex.getMessage()).isEqualTo("registry is down");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void causeConstructor_setsMessageAndCause() {
        var cause = new RuntimeException("connection refused");
        var ex = new UpstreamException("registry unreachable", cause);
        assertThat(ex.getMessage()).isEqualTo("registry unreachable");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
