package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * One artifact a DataStructure model references that is not the DataStructure's own.
 *
 * @param target the logical URN of the referenced artifact
 * @param embedded whether a {@code $ref} names it, so its content becomes part of the model; an
 *     {@code x-core-ref} alone only links to it
 * @param pinned whether every {@code $ref} to it names a concrete version, neither the logical URN
 *     nor {@code :latest}; for a target that is only linked, whether every link does
 * @param mintedBy the logical URN of the DataStructure that minted the target, or {@code null}
 *     when the registry holds no such DataStructure
 */
public record ForeignReference(String target, boolean embedded, boolean pinned, String mintedBy) {

    public ForeignReference {
        Objects.requireNonNull(target, "target");
    }
}
