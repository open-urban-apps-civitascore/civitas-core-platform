package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.specification.base.TenantAwareSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.EqualIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "email", params = "email", spec = EqualIgnoreCase.class)
interface UserEmailSpec extends TenantAwareSpec<User> {}

@Spec(path = "firstName", params = "firstName", spec = LikeIgnoreCase.class)
interface UserFirstNameSpec extends TenantAwareSpec<User> {}

@Spec(path = "lastName", params = "lastName", spec = LikeIgnoreCase.class)
interface UserLastNameSpec extends TenantAwareSpec<User> {}

@Spec(path = "active", params = "active", spec = Equal.class)
interface UserActiveSpec extends TenantAwareSpec<User> {}

@Spec(path = "externalId", params = "externalId", spec = Equal.class)
interface UserExternalIdSpec extends TenantAwareSpec<User> {}

@Or({
  @Spec(path = "firstName", params = "q", spec = LikeIgnoreCase.class),
  @Spec(path = "lastName", params = "q", spec = LikeIgnoreCase.class),
  @Spec(path = "email", params = "q", spec = LikeIgnoreCase.class)
})
interface UserQuickSearchSpec extends TenantAwareSpec<User> {}

public interface UserSpec
    extends UserEmailSpec,
        UserFirstNameSpec,
        UserLastNameSpec,
        UserActiveSpec,
        UserExternalIdSpec,
        UserQuickSearchSpec {}
