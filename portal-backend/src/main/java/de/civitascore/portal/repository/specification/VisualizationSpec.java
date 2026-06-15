package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "dataset.id", pathVars = "datasetId", spec = Equal.class)
public interface VisualizationSpec extends BaseSpec<Dashboard> {}
