package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.application.ElementCommandService;
import de.civitascore.modelforge.application.ElementQueryService;
import de.civitascore.modelforge.application.EmbeddedModelForgeOperations;
import de.civitascore.modelforge.application.ReferenceExistenceValidator;
import de.civitascore.modelforge.application.SchemaImportService;
import de.civitascore.modelforge.application.SmartDataModelsService;
import de.civitascore.modelforge.application.ViewService;
import de.civitascore.modelforge.application.XRepositoryService;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ForeignReference;
import de.civitascore.modelforge.contract.ForeignReferencesQuery;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Which references of a Data Structure model leave it, asked of a real registry: the stored
 * references and {@code $defs} keys are what tell a Data Structure's own members from the members
 * of another it merely lists.
 */
class ForeignReferencesDatabaseTest extends AbstractRegistryDatabaseTest {

    private final UUID bravoId = UUID.randomUUID();
    private final UUID charlieId = UUID.randomUUID();

    private ModelForge modelForge;
    private String bravoMember;
    private String charlieMember;
    private String charliePin;

    @BeforeEach
    void storeTwoDataStructures() {
        modelForge = new EmbeddedModelForgeOperations(
            mock(SchemaImportService.class),
            new ElementQueryService(registry),
            mock(ElementCommandService.class),
            mock(ViewService.class),
            mock(ModelValidator.class),
            mock(CoreSchemaValidator.class),
            new ReferenceExistenceValidator(registry, new SchemaRefExtractor()),
            new DependencyGraphService(registry),
            registry,
            mock(SmartDataModelsService.class),
            mock(XRepositoryService.class),
            urns);
        bravoMember = member(bravoId, "BravoInner", "innerSecret");
        registry.storeDataStructure(urns.dataStructure(bravoId), defs("BravoInner", bravoMember), VersionBump.PATCH, null);
        charlieMember = member(charlieId, "Own", "name");
        // Charlie lists Bravo's member next to its own, as a bare reference stored before the check.
        charliePin = registry.storeDataStructure(urns.dataStructure(charlieId),
            defs("Own", charlieMember, "Stolen", bravoMember), VersionBump.PATCH, null);
    }

    @Test
    @DisplayName("Every Data Structure that lists an Element, in any version, is found")
    void everyDataStructureThatListsAnElementIsFound() {
        assertThat(registry.dataStructuresListing(bravoMember)).containsExactlyInAnyOrder(
            UrnParser.logicalUrn(urns.dataStructure(bravoId)), UrnParser.logicalUrn(urns.dataStructure(charlieId)));
    }

    @Test
    @DisplayName("The member keys of a Data Structure span all of its versions")
    void theMemberKeysOfADataStructureSpanAllOfItsVersions() {
        registry.storeDataStructure(urns.dataStructure(charlieId), defs("Own", charlieMember), VersionBump.MAJOR, null);

        assertThat(registry.dataStructureMemberKeys(urns.dataStructure(charlieId))).containsExactlyInAnyOrder("Own", "Stolen");
    }

    @Test
    @DisplayName("A stored version reports the member it lists but another Data Structure minted")
    void aStoredVersionReportsTheMemberAnotherDataStructureMinted() {
        var references = modelForge.foreignReferences(
            new ForeignReferencesQuery(charlieId, null, new ArtifactId(charliePin)));

        assertThat(references).containsExactly(new ForeignReference(
            UrnParser.logicalUrn(bravoMember), true, true, UrnParser.logicalUrn(urns.dataStructure(bravoId))));
    }

    @Test
    @DisplayName("A document may name a member of an earlier version of its own Data Structure")
    void aDocumentMayNameAMemberOfAnEarlierVersionOfItsOwnDataStructure() {
        JsonNode document = mapper.readTree("""
            {"$ref": "#/$defs/Next", "$defs": {"Next": {"type": "object", "properties": {"own": {"$ref": "%s"}}}}}
            """.formatted(UrnParser.logicalUrn(charlieMember)));

        assertThat(modelForge.foreignReferences(new ForeignReferencesQuery(charlieId, document, null))).isEmpty();
    }

    private String member(UUID dataStructureId, String key, String property) {
        ObjectNode schema = (ObjectNode) mapper.readTree("""
            {"type": "object", "properties": {"%s": {"type": "string"}}}
            """.formatted(property));
        schema.put("$id", urns.urn("element", UrnParser.memberId(dataStructureId, key)));
        return registry.storeElement(schema, Set.of());
    }

    private JsonNode defs(String... keysAndPins) {
        ObjectNode defs = mapper.createObjectNode();
        for (int i = 0; i < keysAndPins.length; i += 2) {
            defs.putObject(keysAndPins[i]).put("$ref", keysAndPins[i + 1]);
        }
        ObjectNode structure = mapper.createObjectNode();
        structure.put("title", "structure");
        structure.set("$defs", defs);
        return structure;
    }
}
