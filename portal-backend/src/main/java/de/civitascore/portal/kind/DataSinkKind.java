package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.DataSinkType;

/** What the platform knows about one kind of Data sink. One Spring bean for each kind. */
public interface DataSinkKind {

  /** The persisted id of this kind. */
  DataSinkType id();
}
