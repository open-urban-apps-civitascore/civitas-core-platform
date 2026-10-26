package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.LikeConcatenated;
import net.kaczmarzyk.spring.data.jpa.domain.EqualIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@FilterParam(name = "email", description = "Filter by email (exact match, case-insensitive).")
@Spec(path = "email", params = "email", spec = EqualIgnoreCase.class)
interface UserEmailSpec extends BaseSpec<User> {}

@FilterParam(
    name = "firstName",
    description = "Filter by first name (partial match, case-insensitive).")
@Spec(path = "firstName", params = "firstName", spec = LikeIgnoreCase.class)
interface UserFirstNameSpec extends BaseSpec<User> {}

@FilterParam(
    name = "lastName",
    description = "Filter by last name (partial match, case-insensitive).")
@Spec(path = "lastName", params = "lastName", spec = LikeIgnoreCase.class)
interface UserLastNameSpec extends BaseSpec<User> {}

@FilterParam(
    name = "q",
    description = "Search in full name or email (partial match, case-insensitive).")
@Or({
  @Spec(
      path = "firstName,lastName",
      params = "q",
      paramSeparator = ' ',
      spec = LikeConcatenated.class),
  @Spec(path = "email", params = "q", spec = LikeIgnoreCase.class)
})
interface UserQuickSearchSpec extends BaseSpec<User> {}

/** JPA Specification for filtering {@link User} entities via query parameters. */
public interface UserSpec
    extends UserEmailSpec, UserFirstNameSpec, UserLastNameSpec, UserQuickSearchSpec {}
