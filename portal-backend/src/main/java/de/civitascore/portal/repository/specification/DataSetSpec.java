package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@FilterParam(
    name = "datapoolIds",
    description =
        "Filter by datapool IDs. Returns datasets assigned to any of the listed datapools.")
@Spec(path = "dataPool.id", params = "datapoolIds", paramSeparator = ',', spec = In.class)
interface DataSetDataPoolSpec extends NamedEntitySpec<DataSet> {}

@FilterParam(
    name = "includePendingDelete",
    description = "Include datasets with pendingSagaType DELETE.",
    type = Boolean.class)
@Spec(
    path = "pendingSagaType",
    params = "includePendingDelete",
    defaultVal = "false",
    spec = PendingDeleteSpec.class)
interface DataSetFilteringSpec extends NamedEntitySpec<DataSet> {}

/** JPA Specification for filtering {@link DataSet} entities via query parameters. */
public interface DataSetSpec
    extends NamedEntitySpec<DataSet>, DataSetDataPoolSpec, DataSetFilteringSpec {}
