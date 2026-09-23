package de.civitascore.portal.model.embedded;

/** What physically exists for a single DataSink, as the last completed saga left it. */
public enum ProvisioningStatus {
  /** Nothing physical exists, so no change to the sink can destroy data. */
  NOT_PROVISIONED,

  PROVISIONED
}
