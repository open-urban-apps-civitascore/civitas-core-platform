package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

@FilterParam(name = "dataSourceStatus", description = "Filter by status (exact match).")
@Spec(path = "dataSourceStatus", params = "dataSourceStatus", spec = Equal.class)
interface DataSourceStatusSpec extends Specification<DataSource> {}

@FilterParam(name = "connectorType", description = "Filter by connector type (exact match).")
@Spec(path = "connectorType", params = "connectorType", spec = Equal.class)
interface DataSourceConnectorTypeSpec extends Specification<DataSource> {}

@FilterParam(
    name = "datapoolId",
    description =
        "Filter by DataPool scope. Returns DataSources with scope type ALL, or SPECIFIC DataSources"
            + " that include this DataPool.")
@Spec(path = "id", params = "datapoolId", spec = DataSourceScopedDatapoolSpec.class)
interface DataSourceScopedDatapoolFilter extends Specification<DataSource> {}

@FilterParam(
    name = "datapoolScopeType",
    description = "Filter by DataPool scope type (exact match).")
@Spec(path = "datapoolScopeType", params = "datapoolScopeType", spec = Equal.class)
interface DataSourceDatapoolScopeTypeSpec extends Specification<DataSource> {}

/** JPA Specification for filtering {@link DataSource} entities via query parameters. */
public interface DataSourceSpec
    extends NamedEntitySpec<DataSource>,
        DataSourceStatusSpec,
        DataSourceConnectorTypeSpec,
        DataSourceScopedDatapoolFilter,
        DataSourceDatapoolScopeTypeSpec {}
