package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DataStructure Entity Tests")
class DataStructureTest {

  private DataStructure dataStructureWithId(UUID id) {
    DataStructure ds = new DataStructure();
    ds.setId(id);
    ds.setName("DataStructure-" + id.toString().substring(0, 8));
    return ds;
  }

  private DataStructureVersion versionWithId(UUID id) {
    DataStructureVersion version = new DataStructureVersion();
    version.setId(id);
    version.setVersion("v" + id.toString().substring(0, 4));
    return version;
  }

  @Nested
  @DisplayName("setDataStructureVersions()")
  class SetDataStructureVersionsTests {

    @Test
    @DisplayName("Should set back-references on versions")
    void shouldSetBackReferencesOnVersions() {
      DataStructure ds = dataStructureWithId(UUID.randomUUID());
      DataStructureVersion v1 = versionWithId(UUID.randomUUID());
      DataStructureVersion v2 = versionWithId(UUID.randomUUID());

      ds.setDataStructureVersions(new HashSet<>(Set.of(v1, v2)));

      assertThat(ds.getDataStructureVersions()).containsExactlyInAnyOrder(v1, v2);
      assertThat(v1.getDataStructure()).isSameAs(ds);
      assertThat(v2.getDataStructure()).isSameAs(ds);
    }

    @Test
    @DisplayName("Should handle null input by clearing versions")
    void shouldHandleNullInput() {
      DataStructure ds = dataStructureWithId(UUID.randomUUID());
      DataStructureVersion v1 = versionWithId(UUID.randomUUID());
      ds.setDataStructureVersions(new HashSet<>(Set.of(v1)));

      ds.setDataStructureVersions(null);

      assertThat(ds.getDataStructureVersions()).isEmpty();
    }

    @Test
    @DisplayName("Should clear existing versions when setting new ones")
    void shouldClearExistingVersions() {
      DataStructure ds = dataStructureWithId(UUID.randomUUID());
      DataStructureVersion old = versionWithId(UUID.randomUUID());
      ds.setDataStructureVersions(new HashSet<>(Set.of(old)));

      DataStructureVersion newVersion = versionWithId(UUID.randomUUID());
      ds.setDataStructureVersions(new HashSet<>(Set.of(newVersion)));

      assertThat(ds.getDataStructureVersions()).containsExactly(newVersion);
      assertThat(ds.getDataStructureVersions()).doesNotContain(old);
      assertThat(newVersion.getDataStructure()).isSameAs(ds);
    }
  }
}
