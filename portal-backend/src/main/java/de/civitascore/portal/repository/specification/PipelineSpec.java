package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.specification.base.DataSetOwnedSpec;

/** JPA Specification for filtering {@link Pipeline} entities via query parameters. */
public interface PipelineSpec extends DataSetOwnedSpec<Pipeline> {}
