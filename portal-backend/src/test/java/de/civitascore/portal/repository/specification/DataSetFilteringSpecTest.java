package de.civitascore.portal.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.kaczmarzyk.spring.data.jpa.utils.QueryContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSetFilteringSpecTest {

  @Mock private Root<DataSet> root;

  @Mock private CriteriaQuery<?> query;

  @Mock private CriteriaBuilder cb;

  @Test
  @DisplayName("Should exclude DELETE saga and keep datasets without a pending saga")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void withoutPendingDelete_shouldExcludeDeleteSaga() {
    Path pendingSagaTypePath = mock(Path.class);
    Predicate noPendingSaga = mock(Predicate.class);
    Predicate notDelete = mock(Predicate.class);
    Predicate withoutPendingDelete = mock(Predicate.class);
    when(root.get("pendingSagaType")).thenReturn(pendingSagaTypePath);
    when(cb.isNull(pendingSagaTypePath)).thenReturn(noPendingSaga);
    when(cb.notEqual(pendingSagaTypePath, PendingSagaType.DELETE)).thenReturn(notDelete);
    when(cb.or(noPendingSaga, notDelete)).thenReturn(withoutPendingDelete);

    PendingDeleteSpec<DataSet> spec =
        new PendingDeleteSpec<>(
            mock(QueryContext.class), "pendingSagaType", new String[] {"false"}, null);
    Predicate result = spec.toPredicate(root, query, cb);

    assertThat(result).isEqualTo(withoutPendingDelete);
    verify(cb).notEqual(pendingSagaTypePath, PendingSagaType.DELETE);
  }
}
