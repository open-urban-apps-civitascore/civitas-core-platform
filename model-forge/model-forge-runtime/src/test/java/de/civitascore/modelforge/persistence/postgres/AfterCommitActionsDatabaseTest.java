package de.civitascore.modelforge.persistence.postgres;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitActionsDatabaseTest extends AbstractRegistryDatabaseTest {

    @Test
    @DisplayName("A failing after-commit action neither fails the committed unit nor skips the next action")
    void aFailingActionDoesNotStopTheNextOne() {
        AtomicBoolean laterActionRan = new AtomicBoolean();

        String pin = registry.inTransaction(() -> {
            registry.afterCommit(() -> {
                throw new IllegalStateException("the graph refresh fails");
            });
            registry.afterCommit(() -> laterActionRan.set(true));
            return registry.storeElement(mapper.readTree("""
                {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object"}
                """), Set.of());
        });

        assertThat(laterActionRan).isTrue();
        assertThat(registry.fetch(pin)).isPresent();
    }
}
