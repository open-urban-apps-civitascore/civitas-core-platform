package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serial;
import java.util.UUID;
import net.kaczmarzyk.spring.data.jpa.utils.Converter;
import net.kaczmarzyk.spring.data.jpa.utils.QueryContext;
import org.springframework.data.jpa.domain.Specification;

/**
 * Custom kaczmarzyk-compatible specification that filters DataSources by Datapool scope.
 *
 * <p>When a {@code datapoolId} query parameter is present, only DataSources allowed for that
 * DataPool are returned:
 *
 * <ul>
 *   <li>{@code datapoolScopeType = ALL} → always included
 *   <li>{@code datapoolScopeType = SPECIFIC} with the given DataPool in {@code scopedDataPools} →
 *       included
 *   <li>{@code datapoolScopeType = NONE} → never included
 * </ul>
 *
 * <p>Uses a correlated EXISTS subquery instead of a JOIN to avoid duplicate rows in paginated
 * results.
 */
public class DataSourceScopedDatapoolSpec implements Specification<DataSource> {

  @Serial private static final long serialVersionUID = 1L;

  private final String[] httpParamValues;

  public DataSourceScopedDatapoolSpec(
      QueryContext queryContext, String path, String[] httpParamValues, Converter converter) {
    this.httpParamValues = httpParamValues;
  }

  @Override
  public Predicate toPredicate(Root<DataSource> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    UUID datapoolId = UUID.fromString(httpParamValues[0]);
    return DataSourceDatapoolUsability.usableInPool(datapoolId).toPredicate(root, query, cb);
  }
}
