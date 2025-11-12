package de.civitascore.portal.repository.specification.base;

import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Base specification for entities with title and description (NamedEntity).
 *
 * <p>Provides filtering by:
 *
 * <ul>
 *   <li>title: partial match (case-insensitive)
 *   <li>description: partial match (case-insensitive)
 *   <li>q: search in title or description
 * </ul>
 */
@Spec(path = "title", params = "title", spec = LikeIgnoreCase.class)
interface NamedEntityTitleSpec<T> extends TenantAwareSpec<T> {}

@Spec(path = "description", params = "description", spec = LikeIgnoreCase.class)
interface NamedEntityDescriptionSpec<T> extends TenantAwareSpec<T> {}

@Spec(path = "title", params = "q", spec = LikeIgnoreCase.class)
interface NamedEntityTitleSearchSpec<T> extends TenantAwareSpec<T> {}

@Spec(path = "description", params = "q", spec = LikeIgnoreCase.class)
interface NamedEntityDescriptionSearchSpec<T> extends TenantAwareSpec<T> {}

public interface NamedEntitySpec<T>
    extends NamedEntityTitleSpec<T>,
        NamedEntityDescriptionSpec<T>,
        NamedEntityTitleSearchSpec<T>,
        NamedEntityDescriptionSearchSpec<T> {}
