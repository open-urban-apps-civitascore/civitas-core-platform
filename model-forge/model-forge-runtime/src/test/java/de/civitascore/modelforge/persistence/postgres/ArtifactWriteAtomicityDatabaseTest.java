package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A failed import leaves neither a partial version nor a partial reference graph.
 *
 * <p>An import stores a group and its members as one unit, so a member rejected midway must not
 * leave the earlier members behind. The rollback is observed by re-reading the tables, because a
 * write that half-committed would raise the same exception.
 */
class ArtifactWriteAtomicityDatabaseTest extends AbstractRegistryDatabaseTest {

    /** An XSD carrying a DOCTYPE — the entity-expansion shape the registry refuses on write. */
    private static final String XSD_WITH_DOCTYPE = """
        <?xml version="1.0"?>
        <!DOCTYPE schema [ <!ENTITY payload "expanded"> ]>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:reading">
          <xs:element name="Reading" type="xs:string"/>
        </xs:schema>
        """;

    @Test
    @DisplayName("An import whose later member carries unsafe XML keeps none of its earlier members")
    void unsafeMemberRollsBackTheWholeImport() {
        assertThatThrownBy(() -> registry.inTransaction(() -> {
            registry.storeElement("Station", schema("temperature"), Set.of());
            return registry.storeXsdElement(urns.mintElement("Reading"), XSD_WITH_DOCTYPE, Set.of());
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsafe XML");

        assertThat(rows("artifact")).as("the accepted member must not survive the rejected one").isEmpty();
        assertThat(rows("artifact_version")).isEmpty();
        assertThat(rows("artifact_representation")).isEmpty();
    }

    @Test
    @DisplayName("A rejected import leaves no reference edges from the members it had already written")
    void rejectedImportLeavesNoPartialReferenceGraph() {
        // A previously imported Element the new group's members legitimately reference.
        String existingPin = registry.storeElement("Target", schema("value"), Set.of());
        int edgesBefore = rows("artifact_reference").size();

        assertThatThrownBy(() -> registry.inTransaction(() -> {
            registry.storeElement("Referrer", schema("value"), Set.of(existingPin));
            return registry.storeXsdElement(urns.mintElement("Reading"), XSD_WITH_DOCTYPE, Set.of());
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(rows("artifact_reference"))
            .as("the reference graph must not retain edges from a rolled-back import")
            .hasSize(edgesBefore);
        assertThat(rows("artifact")).as("only the previously imported artifact remains").hasSize(1);
    }

    @Test
    @DisplayName("A successful import keeps every member and its reference edges")
    void successfulImportKeepsTheWholeUnit() {
        String targetPin = registry.storeElement("Target", schema("value"), Set.of());

        String referrerPin = registry.inTransaction(
            () -> registry.storeElement("Referrer", schema("value"), Set.of(targetPin)));

        assertThat(registry.fetch(referrerPin)).isPresent();
        assertThat(registry.fetchArtifactRefUrns(referrerPin)).contains(targetPin);
    }

    @Test
    @DisplayName("An import aborted after several members keeps none of them")
    void abortedImportKeepsNoMember() {
        // Stands for the import service rejecting a later member (a schema that fails validation):
        // the unit of work is the whole group, so every member written so far must go.
        assertThatThrownBy(() -> registry.inTransaction(() -> {
            registry.storeElement("Station", schema("temperature"), Set.of());
            registry.storeElement("Sensor", schema("humidity"), Set.of());
            registry.storePipeline("Ingest", mapper.readTree("{\"nodes\":[]}"), VersionBump.PATCH);
            throw new IllegalStateException("a later member of the import was rejected");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rows("artifact")).isEmpty();
        assertThat(rows("artifact_version")).isEmpty();
    }

    private JsonNode schema(String property) {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "%s": { "type": "number" } }
            }
            """.formatted(property));
    }
}
