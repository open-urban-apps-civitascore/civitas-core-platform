package de.civitascore.portal.modelregistry;

/**
 * Change class the host asks Model Forge to number a new model version at. It is decided by the
 * lifecycle, never by a client: creating a data structure version starts a new major, and editing
 * one advances the minor.
 */
public enum VersionBump {
  MAJOR,
  MINOR
}
