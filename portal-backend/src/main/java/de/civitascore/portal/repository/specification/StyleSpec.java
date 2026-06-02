package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/** JPA Specification for filtering {@link Style} entities via query parameters. */
@Spec(path = "dataSet.id", pathVars = "dataSetId", spec = Equal.class)
public interface StyleSpec extends BaseSpec<Style> {}
