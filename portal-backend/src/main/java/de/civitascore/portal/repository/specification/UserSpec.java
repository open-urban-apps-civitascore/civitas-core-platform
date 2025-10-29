package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.specification.base.TenantAwareSpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * User filtering specification.
 *
 * <p>Supported query parameters: - id: exact match (supports comma-separated list) - email: exact
 * match (case-insensitive) - firstName: partial match (case-insensitive) - lastName: partial match
 * (case-insensitive) - active: exact match (true/false) - externalId: exact match - createdAtFrom:
 * created after date - createdAtTo: created before date - q: search in firstName, lastName, or
 * email
 *
 * <p>Example: GET /api/users?firstName=John&active=true&createdAtFrom=2024-01-01
 */
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
        @Spec(path = "firstName", params = "username", spec = LikeIgnoreCase.class),
        @Spec(path = "lastName", params = "username", spec = LikeIgnoreCase.class),
        @Spec(path = "email", params = "username", spec = LikeIgnoreCase.class)
})
interface UserQuickSearchSpec extends TenantAwareSpec<User> {}

public interface UserSpec
    extends UserEmailSpec,
        UserFirstNameSpec,
        UserLastNameSpec,
        UserActiveSpec,
        UserExternalIdSpec,
        UserQuickSearchSpec {}
