package de.civitascore.portal.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.entity.DataSet;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ScopeFilteringSpecificationTest {

  @Mock private Root<DataSet> root;

  @Mock private CriteriaQuery<?> query;

  @Mock private CriteriaBuilder cb;

  @Test
  @DisplayName("Should return disjunction (false) when allowedIds is null")
  void shouldReturnDisjunctionWhenNull() {
    Predicate falsePredicate = mock(Predicate.class);
    when(cb.disjunction()).thenReturn(falsePredicate);

    Specification<DataSet> spec = ScopeFilteringSpecification.baseEntityById(null);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(falsePredicate);
    verify(cb).disjunction();
  }

  @Test
  @DisplayName("Should return disjunction (false) when allowedIds is empty")
  void shouldReturnDisjunctionWhenEmpty() {
    Predicate falsePredicate = mock(Predicate.class);
    when(cb.disjunction()).thenReturn(falsePredicate);

    Specification<DataSet> spec = ScopeFilteringSpecification.baseEntityById(Set.of());
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(falsePredicate);
  }

  @Test
  @DisplayName("Should create IN predicate for valid IDs")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldCreateInPredicate() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Set<UUID> ids = Set.of(id1, id2);

    Path path = mock(Path.class);
    Predicate inPredicate = mock(Predicate.class);

    when(root.get("id")).thenReturn(path);
    when(path.in(ids)).thenReturn(inPredicate);

    Specification<DataSet> spec = ScopeFilteringSpecification.baseEntityById(ids);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(inPredicate);
  }

  @Test
  @DisplayName("dataSetByScopeOrPool: disjunction (false) when both sets are empty")
  void dataSetByScopeOrPool_bothEmpty_returnsDisjunction() {
    Predicate falsePredicate = mock(Predicate.class);
    when(cb.disjunction()).thenReturn(falsePredicate);

    Specification<DataSet> spec =
        ScopeFilteringSpecification.dataSetByScopeOrPool(Set.of(), Set.of());
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(falsePredicate);
  }

  @Test
  @DisplayName("dataSetByScopeOrPool: filters by id IN when only scope IDs given")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void dataSetByScopeOrPool_scopeOnly() {
    UUID s1 = UUID.randomUUID();
    Set<UUID> scopeIds = Set.of(s1);

    Path idPath = mock(Path.class);
    Predicate idIn = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);
    when(root.get("id")).thenReturn(idPath);
    when(idPath.in(scopeIds)).thenReturn(idIn);
    when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

    Specification<DataSet> spec =
        ScopeFilteringSpecification.dataSetByScopeOrPool(scopeIds, Set.of());
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(orPredicate);
    verify(idPath).in(scopeIds);
  }

  @Test
  @DisplayName("dataSetByScopeOrPool: filters by datapool_id IN when only pool IDs given")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void dataSetByScopeOrPool_poolOnly() {
    UUID p1 = UUID.randomUUID();
    Set<UUID> poolIds = Set.of(p1);

    Path dataPoolPath = mock(Path.class);
    Path poolIdPath = mock(Path.class);
    Predicate poolIn = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);
    when(root.get("dataPool")).thenReturn(dataPoolPath);
    when(dataPoolPath.get("id")).thenReturn(poolIdPath);
    when(poolIdPath.in(poolIds)).thenReturn(poolIn);
    when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

    Specification<DataSet> spec =
        ScopeFilteringSpecification.dataSetByScopeOrPool(Set.of(), poolIds);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(orPredicate);
    verify(poolIdPath).in(poolIds);
  }

  @Test
  @DisplayName("dataSetByScopeOrPool: ORs both id IN and datapool_id IN when both given")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void dataSetByScopeOrPool_bothGiven() {
    UUID s1 = UUID.randomUUID();
    UUID p1 = UUID.randomUUID();
    Set<UUID> scopeIds = Set.of(s1);
    Set<UUID> poolIds = Set.of(p1);

    Path idPath = mock(Path.class);
    Path dataPoolPath = mock(Path.class);
    Path poolIdPath = mock(Path.class);
    Predicate idIn = mock(Predicate.class);
    Predicate poolIn = mock(Predicate.class);
    Predicate orPredicate = mock(Predicate.class);
    when(root.get("id")).thenReturn(idPath);
    when(idPath.in(scopeIds)).thenReturn(idIn);
    when(root.get("dataPool")).thenReturn(dataPoolPath);
    when(dataPoolPath.get("id")).thenReturn(poolIdPath);
    when(poolIdPath.in(poolIds)).thenReturn(poolIn);
    when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

    Specification<DataSet> spec =
        ScopeFilteringSpecification.dataSetByScopeOrPool(scopeIds, poolIds);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(orPredicate);
    verify(idPath).in(scopeIds);
    verify(poolIdPath).in(poolIds);
  }
}
