package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "dataSet.id", pathVars = "dataSetId", spec = Equal.class)
public interface PipelineSpec extends NamedEntitySpec<Pipeline> {}
