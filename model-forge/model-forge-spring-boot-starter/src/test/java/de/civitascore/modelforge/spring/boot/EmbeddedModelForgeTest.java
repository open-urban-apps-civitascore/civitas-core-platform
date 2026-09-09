package de.civitascore.modelforge.spring.boot;

import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.ImportSmartDataModelCommand;
import de.civitascore.modelforge.contract.ImportXRepositoryCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.contract.ValidateInstanceCommand;
import de.civitascore.modelforge.contract.ValidateSchemaCommand;
import de.civitascore.modelforge.contract.ValidationResult;
import de.civitascore.modelforge.contract.XRepositoryHit;
import de.civitascore.modelforge.contract.XRepositorySearchQuery;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedModelForgeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelForgeAutoConfiguration.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withUserConfiguration(TestOperationsConfig.class);

    @Test
    void embeddedConfigExposesFacadeWithoutWebServer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ModelForge.class);
            assertThat(context).doesNotHaveBean("securityFilterChain");
            assertThat(context.getBean(ModelForge.class)
                .validateSchema(new ValidateSchemaCommand(mapper.createObjectNode())).valid()).isTrue();
        });
    }

    @Configuration
    static class TestOperationsConfig {

        @Bean
        ModelForge modelForge() {
            return new ModelForge() {
                @Override
                public ImportResult importSchema(ImportSchemaCommand command) {
                    var id = new ArtifactId("urn:example:model_forge.artifact");
                    return new ImportResult(id, List.of(id), Map.of());
                }

                @Override
                public Optional<ArtifactView> getArtifact(ArtifactId artifactId) {
                    return Optional.empty();
                }

                @Override
                public Optional<ArtifactView> getBundledView(SchemaViewQuery query) {
                    return Optional.empty();
                }

                @Override
                public ValidationResult validateSchema(ValidateSchemaCommand command) {
                    return new ValidationResult(true, List.of());
                }

                @Override
                public ValidationResult validateInstance(ValidateInstanceCommand command) {
                    return new ValidationResult(true, List.of());
                }

                @Override
                public DependencyGraphView dependencies(DependencyQuery query) {
                    return new DependencyGraphView(List.of(), List.of());
                }

                @Override
                public List<ArtifactSummary> search(ArtifactSearchQuery query) {
                    return List.of();
                }

                @Override
                public de.civitascore.modelforge.contract.ArtifactWriteResult createArtifact(
                        de.civitascore.modelforge.contract.CreateArtifactCommand command) {
                    return new de.civitascore.modelforge.contract.ArtifactWriteResult(
                        new ArtifactId("urn:example:model_forge.artifact"), java.util.Map.of());
                }

                @Override
                public de.civitascore.modelforge.contract.ArtifactWriteResult saveArtifact(SaveArtifactCommand command) {
                    return new de.civitascore.modelforge.contract.ArtifactWriteResult(
                        command.artifactId(), java.util.Map.of());
                }

                @Override
                public de.civitascore.modelforge.contract.ArtifactWriteResult bumpVersion(
                        de.civitascore.modelforge.contract.BumpVersionCommand command) {
                    return new de.civitascore.modelforge.contract.ArtifactWriteResult(
                        command.artifactId(), java.util.Map.of());
                }

                @Override
                public void deleteArtifact(ArtifactId artifactId, boolean cascade, boolean force) {
                    // no-op fake
                }

                @Override
                public List<ArtifactSummary> orphans(de.civitascore.modelforge.contract.ArtifactKind kind) {
                    return List.of();
                }

                @Override
                public List<de.civitascore.modelforge.contract.NonConformingArtifact> nonConformingElements() {
                    return List.of();
                }

                @Override
                public void linkToDataSet(ArtifactId dataSet, ArtifactId member) {
                    // no-op fake
                }

                @Override
                public void unlinkFromDataSet(ArtifactId dataSet, ArtifactId member) {
                    // no-op fake
                }

                @Override
                public Optional<ArtifactView> getInlinedView(SchemaViewQuery query) {
                    return Optional.empty();
                }

                @Override
                public DependencyGraphView dependents(DependencyQuery query) {
                    return new DependencyGraphView(List.of(), List.of());
                }

                @Override
                public DependencyGraphView mapsTo(DependencyQuery query) {
                    return new DependencyGraphView(List.of(), List.of());
                }

                @Override
                public DependencyGraphView mappedFrom(DependencyQuery query) {
                    return new DependencyGraphView(List.of(), List.of());
                }

                @Override
                public ImportResult importFromSmartDataModels(ImportSmartDataModelCommand command) {
                    var id = new ArtifactId("urn:example:model_forge.artifact");
                    return new ImportResult(id, List.of(id), Map.of());
                }

                @Override
                public List<XRepositoryHit> searchXRepository(XRepositorySearchQuery query) {
                    return List.of();
                }

                @Override
                public ImportResult importFromXRepository(ImportXRepositoryCommand command) {
                    var id = new ArtifactId("urn:example:model_forge.artifact");
                    return new ImportResult(id, List.of(id), Map.of());
                }
            };
        }
    }
}
