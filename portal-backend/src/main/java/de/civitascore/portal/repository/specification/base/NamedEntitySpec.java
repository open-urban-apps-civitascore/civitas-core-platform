package de.civitascore.portal.repository.specification.base;

import de.civitascore.portal.model.entity.base.NamedEntity;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Base specification for entities with name and description (NamedEntity).
 *
 * <p>Provides filtering by:
 *
 * <ul>
 *   <li>name: partial match (case-insensitive)
 *   <li>description: partial match (case-insensitive)
 *   <li>q: search in name or description
 * </ul>
 */
@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface NamedEntityNameSpec<T> extends BaseSpec<T> {}

@Spec(path = "description", params = "description", spec = LikeIgnoreCase.class)
interface NamedEntityDescriptionSpec<T> extends BaseSpec<T> {}

@Or({
  @Spec(path = "name", params = "q", spec = LikeIgnoreCase.class),
  @Spec(path = "description", params = "q", spec = LikeIgnoreCase.class)
})
interface NamedEntityQuickSearchSpec<T> extends BaseSpec<T> {}

public interface NamedEntitySpec<T extends NamedEntity>
    extends NamedEntityNameSpec<T>, NamedEntityDescriptionSpec<T>, NamedEntityQuickSearchSpec<T> {}
