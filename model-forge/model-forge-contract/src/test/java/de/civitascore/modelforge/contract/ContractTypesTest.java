package de.civitascore.modelforge.contract;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractTypesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void artifactIdRejectsBlankValues() {
        assertThatThrownBy(() -> new ArtifactId(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ArtifactId must not be blank");
    }

    @Test
    void validationResultDerivesValidityFromDiagnostics() {
        var warning = new Diagnostic(DiagnosticSeverity.WARNING, "warning", "warn", "/name");
        var error = new Diagnostic(DiagnosticSeverity.ERROR, "error", "err", "/id");

        assertThat(ValidationResult.of(List.of(warning)).valid()).isTrue();
        assertThat(ValidationResult.of(List.of(warning, error)).valid()).isFalse();
    }

    @Test
    void resultCollectionsAreImmutable() {
        var artifactId = new ArtifactId("urn:example:artifact");
        var result = new ImportResult(artifactId, List.of(artifactId), Map.of());

        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> result.importedArtifactIds().add(artifactId));
    }

    @Test
    void commandsAllowJsonNodeAsExplicitContractType() throws Exception {
        var schema = objectMapper.readTree("{\"type\":\"object\"}");
        var instance = objectMapper.readTree("{\"name\":\"demo\"}");

        var importCommand = new ImportSchemaCommand(schema);
        var validateCommand = new ValidateInstanceCommand(schema, instance);

        assertThat(importCommand.schema()).isSameAs(schema);
        assertThat(validateCommand.instance()).isSameAs(instance);
    }

    @Test
    void bumpVersionCommandDefaultsToPatchAndRequiresAnIdentity() {
        var artifactId = new ArtifactId("urn:core:platform:civitas:datastructure:common:Order:ds00000001");

        assertThat(new BumpVersionCommand(artifactId, null).bump()).isEqualTo(VersionBump.PATCH);
        assertThat(new BumpVersionCommand(artifactId).bump()).isEqualTo(VersionBump.PATCH);
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new BumpVersionCommand(null, VersionBump.MAJOR));
    }

    @Test
    void importSchemaCommandDefaultsItsChangeClass() throws Exception {
        var schema = objectMapper.readTree("{\"type\":\"object\"}");

        assertThat(new ImportSchemaCommand(schema, null, null, false, null).bump())
            .isEqualTo(VersionBump.PATCH);
        assertThat(new ImportSchemaCommand(schema).preserveVersion()).isFalse();
    }
}
