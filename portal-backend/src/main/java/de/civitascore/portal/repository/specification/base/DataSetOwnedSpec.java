package de.civitascore.portal.repository.specification.base;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Base specification for entities nested under a parent dataset. Scopes every query to the {@code
 * dataSetId} named in the path.
 */
@Spec(path = "dataSet.id", pathVars = "dataSetId", spec = Equal.class)
public interface DataSetOwnedSpec<T extends DataSetOwnedEntity> extends BaseSpec<T> {}
