package de.civitascore.portal.model.entity.base;

import jakarta.persistence.MappedSuperclass;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Grouping superclass for scoped data entities (DataSet, DataSource, DataStructure, etc.). */
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class BaseDataEntity extends AssignableEntity {}
