package de.civitascore.portal.model.entity.base;

import jakarta.persistence.MappedSuperclass;

/** Grouping superclass for scoped data entities (DataSet, DataSource, DataStructure, etc.). */
@MappedSuperclass
public abstract class BaseDataEntity extends AssignableEntity {}
