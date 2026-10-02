package de.civitascore.modelforge.contract;

import de.civitascore.modelforge.urn.UrnParser;
import java.io.Serializable;
import java.util.Objects;

/**
 * Stable identifier of a Model Forge artifact, carrying a CORE URN.
 *
 * <p>The value may be either form of a CORE URN:
 * <ul>
 *   <li><b>logical</b> (version-free), e.g.
 *       {@code urn:core:platform:civitas:element:common:0b6f5c1e-8a4d-4f7b-9c2e-3d1a5b7c9e20} — the
 *       stable identity of an artifact across all its versions; resolves to the current
 *       (highest-SemVer) version on read.
 *   <li><b>versioned</b> (a concrete pin), e.g.
 *       {@code …:0b6f5c1e-8a4d-4f7b-9c2e-3d1a5b7c9e20:1.2.0} — one specific stored version.
 * </ul>
 *
 * <p>Read operations on {@link de.civitascore.modelforge.facade.ModelForge} accept both: a logical
 * (or {@code :latest}) URN reads the current version, a versioned URN reads exactly that version.
 * Write operations return a <em>versioned</em> id (see {@link ImportResult}); Model Forge is the
 * sole version authority, so callers never choose the version segment. Use {@link #logicalUrn()}
 * and {@link #version()} to take the id apart.
 */
public record ArtifactId(String value) implements Serializable {

    public ArtifactId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ArtifactId must not be blank");
        }
    }

    /** The version-free identity of this artifact (identical to {@link #value()} when logical). */
    public String logicalUrn() {
        return UrnParser.logicalUrn(value);
    }

    /** The SemVer version segment, or {@code null} when this id is logical (version-free). */
    public String version() {
        return UrnParser.versionFromUrn(value);
    }
}
