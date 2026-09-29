package de.civitascore.portal.frost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.PublishedStructure;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.PublishedStructureRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Publishing the port structures to the registry")
class PublishedStructureRegistrarTest {

  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private PublishedStructureRepository publishedStructureRepository;

  private PublishedStructureRegistrar registrar;
  private final Map<String, PublishedStructure> rows = new HashMap<>();

  @BeforeEach
  void setUp() {
    registrar = new PublishedStructureRegistrar(modelRegistryGateway, publishedStructureRepository);
    rows.clear();
    // A repository that remembers, so a second run sees what the first one wrote.
    when(publishedStructureRepository.findByPort(any()))
        .thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0, String.class))));
  }

  private void acceptWrites() {
    when(modelRegistryGateway.storeModel(any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            call -> {
              String port = call.getArgument(1, String.class);
              String logical = PortStructureGenerator.logicalUrn(port);
              return new ModelPin(logical, logical + ":1.0.0", "1.0.0");
            });
    when(publishedStructureRepository.save(any()))
        .thenAnswer(
            call -> {
              PublishedStructure saved = call.getArgument(0, PublishedStructure.class);
              rows.put(saved.getPort(), saved);
              return saved;
            });
  }

  @Test
  @DisplayName("Stores every declared structure on the first start")
  void storesEveryStructure() {
    acceptWrites();

    registrar.run(null);

    assertThat(rows.keySet()).containsExactlyInAnyOrder("Things", "Observations", "ThingTree");
    PublishedStructure thingTree = rows.get("ThingTree");
    assertThat(thingTree.getLogicalUrn()).isEqualTo(PortStructureGenerator.logicalUrn("ThingTree"));
    // The version the registry assigned is what an import pins.
    assertThat(thingTree.getVersionedUrn()).endsWith(":1.0.0");
  }

  @Test
  @DisplayName("Begins the history of a structure it has not seen")
  void beginsTheHistory() {
    acceptWrites();

    registrar.run(null);

    ArgumentCaptor<VersionBump> bump = ArgumentCaptor.forClass(VersionBump.class);
    verify(modelRegistryGateway, org.mockito.Mockito.times(3))
        .storeModel(any(), any(), any(), eq(null), bump.capture(), eq(null));
    assertThat(bump.getAllValues()).containsOnly(VersionBump.MAJOR);
  }

  @Test
  @DisplayName("Writes nothing on a restart with an unchanged declaration")
  void isIdempotent() {
    acceptWrites();
    registrar.run(null);

    registrar.run(null);

    // Three writes in total, from the first run. A restart that minted a version would move every
    // pin and make each Dataset look changed.
    verify(modelRegistryGateway, org.mockito.Mockito.times(3))
        .storeModel(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Revises a structure whose declaration changed")
  void revisesAChangedStructure() {
    acceptWrites();
    // A pin from an earlier release: same identity, a hash that no longer matches.
    PublishedStructure stale = new PublishedStructure();
    stale.setPort("Things");
    stale.setLogicalUrn(PortStructureGenerator.logicalUrn("Things"));
    stale.setVersionedUrn(PortStructureGenerator.logicalUrn("Things") + ":1.0.0");
    stale.setContentHash("a hash of something else");
    rows.put("Things", stale);

    registrar.run(null);

    verify(modelRegistryGateway)
        .storeModel(
            eq(Optional.of(PortStructureGenerator.logicalUrn("Things"))),
            eq("Things"),
            any(),
            eq(null),
            // The shape of a port does not turn into another port, so the change stays inside the
            // major.
            eq(VersionBump.MINOR),
            eq(null));
    verify(modelRegistryGateway, never())
        .storeModel(any(), eq("Things"), any(), any(), eq(VersionBump.MAJOR), any());
  }
}
