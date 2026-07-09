package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "dataPool.id", params = "datapoolIds", paramSeparator = ',', spec = In.class)
interface DataSetDataPoolSpec extends NamedEntitySpec<DataSet> {}

/** JPA Specification for filtering {@link DataSet} entities via query parameters. */
public interface DataSetSpec extends NamedEntitySpec<DataSet>, DataSetDataPoolSpec {}
