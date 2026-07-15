package de.civitascore.portal.modelregistry;

/**
 * Host-side change classification chosen by the client for a new model version. It is mapped to the
 * Model Forge bump inside {@link ModelRegistryGateway}; Model Forge remains the version authority
 * and assigns the concrete SemVer version. Only applies when a new version of an existing model is
 * stored — the very first version is always the initial version regardless of the bump.
 */
public enum VersionBump {
  MAJOR,
  MINOR,
  PATCH
}
