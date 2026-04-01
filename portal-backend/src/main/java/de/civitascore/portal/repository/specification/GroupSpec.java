package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "contactUser.id", params = "contactUserId", spec = Equal.class)
interface GroupContactUserIdSpec extends NamedEntitySpec<Group> {}

@Spec(path = "parentGroup.id", params = "parentGroupId", spec = Equal.class)
interface GroupParentGroupIdSpec extends NamedEntitySpec<Group> {}

@Spec(path = "members.id", params = "memberId", paramSeparator = ',', spec = In.class)
interface GroupMemberIdSpec extends NamedEntitySpec<Group> {}

/** JPA Specification for filtering {@link Group} entities via query parameters. */
public interface GroupSpec
    extends GroupContactUserIdSpec, GroupParentGroupIdSpec, GroupMemberIdSpec {}
