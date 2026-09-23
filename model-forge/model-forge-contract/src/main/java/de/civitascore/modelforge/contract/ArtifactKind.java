package de.civitascore.modelforge.contract;

/**
 * Public artifact categories a consumer can save through {@link SaveArtifactCommand}.
 *
 * <p>There is no separate kind for XSD: an XSD-backed Element is an {@link #ELEMENT} whose stored
 * representation format is XSD rather than JSON Schema. The save path recognises it from the
 * content (a textual XSD document rather than a JSON Schema object), so the URN and the kind stay
 * format-agnostic ({@code :element:}).
 */
public enum ArtifactKind {
    ELEMENT,
    DATA_SET,
    DATA_STRUCTURE,
    MAPPING,
    PIPELINE,
    DATA_SOURCE,
    DATA_SINK
}
