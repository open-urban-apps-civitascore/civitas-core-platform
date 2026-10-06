package de.civitascore.portal.model.input;

/**
 * How an install identifies the data structures of a package.
 *
 * <ul>
 *   <li>{@link #MINT}: every structure becomes a copy under a URN this instance mints, and the
 *       package URN is kept as its origin. The default.
 *   <li>{@link #KEEP}: structures keep the URN the package gives them, so they are the same
 *       structures on every instance that installs them this way. A structure this instance already
 *       holds under that URN is reused, not copied. Experimental.
 * </ul>
 *
 * <p>Only data structures and their elements are concerned. Sources, datasets, mappings, sinks and
 * pipelines are always minted.
 */
public enum StructureIdentity {
  MINT,
  KEEP
}
