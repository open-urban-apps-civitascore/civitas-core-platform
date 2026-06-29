package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

@Spec(path = "dataSourceStatus", params = "dataSourceStatus", spec = Equal.class)
interface DataSourceStatusSpec extends Specification<DataSource> {}

@Spec(path = "connectorType", params = "connectorType", spec = Equal.class)
interface DataSourceConnectorTypeSpec extends Specification<DataSource> {}

@Spec(path = "id", params = "datapoolId", spec = DataSourceScopedDatapoolSpec.class)
interface DataSourceScopedDatapoolFilter extends Specification<DataSource> {}

@Spec(path = "datapoolScopeType", params = "datapoolScopeType", spec = Equal.class)
interface DataSourceDatapoolScopeTypeSpec extends Specification<DataSource> {}

/** JPA Specification for filtering {@link DataSource} entities via query parameters. */
public interface DataSourceSpec
    extends NamedEntitySpec<DataSource>,
        DataSourceStatusSpec,
        DataSourceConnectorTypeSpec,
        DataSourceScopedDatapoolFilter,
        DataSourceDatapoolScopeTypeSpec {}
