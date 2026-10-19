package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.embedded.PendingSagaType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.kaczmarzyk.spring.data.jpa.domain.PathSpecification;
import net.kaczmarzyk.spring.data.jpa.utils.Converter;
import net.kaczmarzyk.spring.data.jpa.utils.QueryContext;

/** JPA specification for the default pending-delete filter on datasets. */
public class PendingDeleteSpec<T> extends PathSpecification<T> {

  private final boolean includePendingDelete;

  /** Creates a pending-delete filter from the query parameter value. */
  public PendingDeleteSpec(
      QueryContext queryContext, String path, String[] arguments, Converter converter) {
    super(queryContext, path);
    this.includePendingDelete = Boolean.parseBoolean(arguments[0]);
  }

  @Override
  public Predicate toPredicate(
      Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
    if (includePendingDelete) {
      return criteriaBuilder.conjunction();
    }
    return criteriaBuilder.or(
        criteriaBuilder.isNull(path(root)),
        criteriaBuilder.notEqual(path(root), PendingSagaType.DELETE));
  }
}
