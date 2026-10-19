package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * One artifact a DataStructure model references that is not the DataStructure's own.
 *
 * @param target the logical URN of the referenced artifact
 * @param kind how the model refers to the target
 * @param mintedBy the logical URN of the DataStructure that minted the target, or {@code null}
 *     when the registry holds no such DataStructure
 */
public record ForeignReference(String target, Kind kind, String mintedBy) {

    public ForeignReference {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
    }

    /** How a model refers to a target; a {@code $ref} decides over an {@code x-core-ref}. */
    public enum Kind {

        /** Only {@code x-core-ref} links name the target, so none of its content enters the model. */
        LINKED,

        /** Every {@code $ref} to the target names a concrete version of it. */
        EMBEDDED_PINNED,

        /** A {@code $ref} names the target without a version, so the model follows its newest one. */
        EMBEDDED_FLOATING
    }
}
