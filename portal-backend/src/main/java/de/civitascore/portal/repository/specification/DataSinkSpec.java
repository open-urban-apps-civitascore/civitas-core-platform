package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/** JPA Specification for filtering {@link DataSink} entities via query parameters. */
@Spec(path = "pipeline.dataSet.id", pathVars = "dataSetId", spec = Equal.class)
public interface DataSinkSpec extends BaseSpec<DataSink> {}
