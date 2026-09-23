package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.core.port.ArtifactSearchCriteria;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit that answers "which stored Elements would a re-save now refuse". Runs against the real
 * registry because the question is about rows already in the tables, not about a validator in
 * isolation.
 */
class NonConformingElementAuditDatabaseTest extends AbstractRegistryDatabaseTest {

    private final ModelValidator validator = new ModelValidator();

    private static final String BAD = "urn:core:platform:civitas:element:common:Legacy:aa11bb22";
    private static final String GOOD = "urn:core:platform:civitas:element:common:Fine:cc33dd44";

    /**
     * Reads of a non-conforming Element must keep working — the audit exists precisely because such
     * rows stay readable, and a failure to fetch would hide them from the inventory instead.
     */
    @Test
    void aStoredNonConformingElementIsFoundAndStaysReadable() {
        registry.storeElement(BAD, mapper.readTree("""
            {"$schema":"https://json-schema.org/draft/2020-12/schema",
             "$id":"%s","title":"Legacy","type":42}""".formatted(BAD)),
            Set.of(), Set.of(), null, VersionBump.PATCH);
        registry.storeElement(GOOD, mapper.readTree("""
            {"$schema":"https://json-schema.org/draft/2020-12/schema",
             "$id":"%s","title":"Fine","type":"object"}""".formatted(GOOD)),
            Set.of(), Set.of(), null, VersionBump.PATCH);

        List<String> nonConforming = registry.searchArtifacts(
                new ArtifactSearchCriteria(null, false, "element", null, null, null, Integer.MAX_VALUE, 0))
            .stream()
            .map(hit -> UrnParser.logicalUrn(hit.logicalId()))
            .filter(urn -> registry.fetch(urn)
                .map(schema -> !validator.validateSchema(schema).isEmpty())
                .orElse(false))
            .toList();

        assertThat(nonConforming).containsExactly(BAD);

        JsonNode stillReadable = registry.fetch(BAD).orElseThrow();
        assertThat(stillReadable.path("type").asInt()).isEqualTo(42);
    }
}
