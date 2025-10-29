package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Group-specific filtering. Inherits: id, createdAt, modifiedAt, tenantId, title, description, q
 * from base specs.
 *
 * <p>Additional filters: - name, contactUserId, parentGroupId
 */
@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface GroupNameSpec extends NamedEntitySpec<Group> {}

@Spec(path = "contactUser.id", params = "contactUserId", spec = Equal.class)
interface GroupContactUserIdSpec extends NamedEntitySpec<Group> {}

@Spec(path = "parentGroup.id", params = "parentGroupId", spec = Equal.class)
interface GroupParentGroupIdSpec extends NamedEntitySpec<Group> {}

public interface GroupSpec extends GroupNameSpec, GroupContactUserIdSpec, GroupParentGroupIdSpec {}
