package de.civitascore.modelforge.contract;

import java.util.List;
import java.util.Objects;

/** The outcome of {@link de.civitascore.modelforge.facade.ModelForge#checkRoot}. */
public sealed interface RootCheck {

    /** The model has a root, and every member is reachable from it. */
    record Rooted() implements RootCheck {}

    /** No member is designated as the root. */
    record NoRoot() implements RootCheck {}

    /** The root exists, but these members, named by their {@code $defs} key, are not reachable from it. */
    record UnreachableMembers(List<String> memberNames) implements RootCheck {

        public UnreachableMembers {
            memberNames = List.copyOf(Objects.requireNonNull(memberNames, "memberNames"));
        }
    }
}
