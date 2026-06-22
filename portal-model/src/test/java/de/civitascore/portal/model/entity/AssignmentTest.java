package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Assignment Entity Tests")
class AssignmentTest {

  // ── Helper methods ────────────────────────────────────────────────────────

  private static Role roleWithType(RoleType type) {
    Role role = new Role();
    role.setId(UUID.randomUUID());
    role.setName("Role-" + type.name());
    role.setRoleType(type);
    return role;
  }

  private static Group newGroup() {
    Group group = new Group();
    group.setId(UUID.randomUUID());
    group.setName("TestGroup");
    return group;
  }

  private static DataSet newDataSet() {
    DataSet ds = new DataSet();
    ds.setId(UUID.randomUUID());
    ds.setName("TestDataSet");
    return ds;
  }

  private static DataSource newDataSource() {
    DataSource ds = new DataSource();
    ds.setId(UUID.randomUUID());
    ds.setName("TestDataSource");
    return ds;
  }

  private static DataStructure newDataStructure() {
    DataStructure ds = new DataStructure();
    ds.setId(UUID.randomUUID());
    ds.setName("TestDataStructure");
    return ds;
  }

  private static Catalog newCatalog() {
    Catalog c = new Catalog();
    c.setId(UUID.randomUUID());
    c.setName("TestCatalog");
    return c;
  }

  private static Assignment baseAssignment(RoleType roleType) {
    Assignment a = new Assignment();
    a.setGroup(newGroup());
    a.setRole(roleWithType(roleType));
    return a;
  }

  /**
   * Invokes the private {@code validateBeforePersist()} method via reflection, which in turn calls
   * both {@code validateScope()} and {@code validateRoleType()}.
   */
  private static void invokeValidateBeforePersist(Assignment assignment) throws Exception {
    Method method = Assignment.class.getDeclaredMethod("validateBeforePersist");
    method.setAccessible(true);
    try {
      method.invoke(assignment);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException re) {
        throw re;
      }
      throw e;
    }
  }

  // ── validateScope() ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("validateScope()")
  class ValidateScopeTests {

    @Test
    @DisplayName("Should pass for DATASET scopeType with dataset set")
    void shouldPassForDatasetScopeWithDataset() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASET);
      a.setDataset(newDataSet());

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should pass for DATASOURCE scopeType with dataSource set")
    void shouldPassForDatasourceScopeWithDatasource() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASOURCE);
      a.setDataSource(newDataSource());

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should pass for DATASTRUCTURE scopeType with dataStructure set")
    void shouldPassForDatastructureScopeWithDatastructure() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASTRUCTURE);
      a.setDataStructure(newDataStructure());

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should pass for CATALOG scopeType with catalog set")
    void shouldPassForCatalogScopeWithCatalog() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.CATALOG);
      a.setCatalog(newCatalog());

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should pass for TENANT scopeType with no scope entity set")
    void shouldPassForTenantScopeWithNoEntity() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.TENANT);

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should pass for null scopeType with no scope entity set")
    void shouldPassForNullScopeWithNoEntity() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScopeType(null);

      assertThatNoException().isThrownBy(a::validateScope);
    }

    @Test
    @DisplayName("Should fail for DATASET scopeType with dataSource set instead of dataset")
    void shouldFailForDatasetScopeWithWrongEntity() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASET);
      a.setDataSource(newDataSource());

      assertThatIllegalStateException()
          .isThrownBy(a::validateScope)
          .withMessageContaining("DataSource requires DATASOURCE scope");
    }

    @Test
    @DisplayName("Should fail for DATASOURCE scopeType with dataset set instead of dataSource")
    void shouldFailForDatasourceScopeWithWrongEntity() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASOURCE);
      a.setDataset(newDataSet());

      assertThatIllegalStateException()
          .isThrownBy(a::validateScope)
          .withMessageContaining("DataSet requires DATASET scope");
    }

    @Test
    @DisplayName("Should fail for TENANT scopeType with a scope entity set")
    void shouldFailForTenantScopeWithEntitySet() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.TENANT);
      a.setDataset(newDataSet());

      assertThatIllegalStateException()
          .isThrownBy(a::validateScope)
          .withMessageContaining("No scope entity should be set for SYSTEM or TENANT scope");
    }

    @Test
    @DisplayName("Should fail for null scopeType with a scope entity set")
    void shouldFailForNullScopeWithEntitySet() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScopeType(null);
      a.setDataStructure(newDataStructure());

      assertThatIllegalStateException()
          .isThrownBy(a::validateScope)
          .withMessageContaining("No scope entity should be set for SYSTEM or TENANT scope");
    }
  }

  // ── validateRoleType() ────────────────────────────────────────────────────

  @Nested
  @DisplayName("validateRoleType() via validateBeforePersist()")
  class ValidateRoleTypeTests {

    @Test
    @DisplayName("Should pass for SYSTEM role with null scopeType")
    void shouldPassForSystemRoleWithNullScope() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScopeType(null);

      assertThatNoException().isThrownBy(() -> invokeValidateBeforePersist(a));
    }

    @Test
    @DisplayName("Should pass for DATA role with non-null scopeType")
    void shouldPassForDataRoleWithScope() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASET);
      a.setDataset(newDataSet());

      assertThatNoException().isThrownBy(() -> invokeValidateBeforePersist(a));
    }

    @Test
    @DisplayName("Should fail for SYSTEM role with non-null scopeType")
    void shouldFailForSystemRoleWithScope() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScopeType(ScopeType.DATASET);
      a.setDataset(newDataSet());

      assertThatIllegalStateException()
          .isThrownBy(() -> invokeValidateBeforePersist(a))
          .withMessageContaining("SYSTEM roles cannot have scope");
    }

    @Test
    @DisplayName("Should fail for DATA role with null scopeType")
    void shouldFailForDataRoleWithNullScope() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(null);

      assertThatIllegalStateException()
          .isThrownBy(() -> invokeValidateBeforePersist(a))
          .withMessageContaining("Only SYSTEM roles can have null scope");
    }
  }

  // ── getScope() ────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getScope()")
  class GetScopeTests {

    @Test
    @DisplayName("Should return DataSet when dataset is set")
    void shouldReturnDataSet() {
      Assignment a = new Assignment();
      DataSet dataSet = newDataSet();
      a.setDataset(dataSet);

      assertThat(a.getScope()).isSameAs(dataSet);
    }

    @Test
    @DisplayName("Should return DataSource when dataSource is set")
    void shouldReturnDataSource() {
      Assignment a = new Assignment();
      DataSource dataSource = newDataSource();
      a.setDataSource(dataSource);

      assertThat(a.getScope()).isSameAs(dataSource);
    }

    @Test
    @DisplayName("Should return DataStructure when dataStructure is set")
    void shouldReturnDataStructure() {
      Assignment a = new Assignment();
      DataStructure dataStructure = newDataStructure();
      a.setDataStructure(dataStructure);

      assertThat(a.getScope()).isSameAs(dataStructure);
    }

    @Test
    @DisplayName("Should return Catalog when catalog is set")
    void shouldReturnCatalog() {
      Assignment a = new Assignment();
      Catalog catalog = newCatalog();
      a.setCatalog(catalog);

      assertThat(a.getScope()).isSameAs(catalog);
    }

    @Test
    @DisplayName("Should return null when no scope entity is set")
    void shouldReturnNullWhenNoScopeEntity() {
      Assignment a = new Assignment();

      assertThat(a.getScope()).isNull();
    }
  }

  // ── setScope() overloads ──────────────────────────────────────────────────

  @Nested
  @DisplayName("setScope() overloads")
  class SetScopeTests {

    @Test
    @DisplayName("setScope(DataStructure) should set entity and DATASTRUCTURE scopeType")
    void shouldSetDataStructureScope() {
      Assignment a = new Assignment();
      DataStructure ds = newDataStructure();

      a.setScope(ds);

      assertThat(a.getDataStructure()).isSameAs(ds);
      assertThat(a.getScopeType()).isEqualTo(ScopeType.DATASTRUCTURE);
    }

    @Test
    @DisplayName("setScope(DataSet) should set entity and DATASET scopeType")
    void shouldSetDataSetScope() {
      Assignment a = new Assignment();
      DataSet ds = newDataSet();

      a.setScope(ds);

      assertThat(a.getDataset()).isSameAs(ds);
      assertThat(a.getScopeType()).isEqualTo(ScopeType.DATASET);
    }

    @Test
    @DisplayName("setScope(DataSource) should set entity and DATASOURCE scopeType")
    void shouldSetDataSourceScope() {
      Assignment a = new Assignment();
      DataSource ds = newDataSource();

      a.setScope(ds);

      assertThat(a.getDataSource()).isSameAs(ds);
      assertThat(a.getScopeType()).isEqualTo(ScopeType.DATASOURCE);
    }

    @Test
    @DisplayName("setScope(Catalog) should set entity and CATALOG scopeType")
    void shouldSetCatalogScope() {
      Assignment a = new Assignment();
      Catalog c = newCatalog();

      a.setScope(c);

      assertThat(a.getCatalog()).isSameAs(c);
      assertThat(a.getScopeType()).isEqualTo(ScopeType.CATALOG);
    }
  }

  // ── validateBeforePersist() integration ───────────────────────────────────

  @Nested
  @DisplayName("validateBeforePersist()")
  class ValidateBeforePersistTests {

    @Test
    @DisplayName("Should pass on fully valid scoped assignment")
    void shouldPassOnValidScopedAssignment() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScope(newDataSet());

      assertThatNoException().isThrownBy(() -> invokeValidateBeforePersist(a));
    }

    @Test
    @DisplayName("Should pass on fully valid unscoped SYSTEM assignment")
    void shouldPassOnValidSystemAssignment() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScopeType(null);

      assertThatNoException().isThrownBy(() -> invokeValidateBeforePersist(a));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when scope is inconsistent with roleType")
    void shouldThrowWhenScopeInconsistentWithRoleType() {
      Assignment a = baseAssignment(RoleType.SYSTEM);
      a.setScope(newDataSet()); // SYSTEM role + non-null scope = invalid

      assertThatIllegalStateException()
          .isThrownBy(() -> invokeValidateBeforePersist(a))
          .withMessageContaining("SYSTEM roles cannot have scope");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when scopeType mismatches scope entity")
    void shouldThrowWhenScopeTypeMismatchesScopeEntity() {
      Assignment a = baseAssignment(RoleType.DATA);
      a.setScopeType(ScopeType.DATASET);
      a.setDataSource(newDataSource()); // scopeType=DATASET but entity is DataSource

      assertThatIllegalStateException().isThrownBy(() -> invokeValidateBeforePersist(a));
    }
  }
}
