package de.civitascore.portal.model.entity;

/** An entity owned by a {@link DataSet}, whose writes follow the parent's lifecycle. */
public interface DataSetOwned {

  DataSet getDataSet();
}
