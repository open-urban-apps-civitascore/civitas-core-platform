package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/** Tenant filtering specification. */
@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface TenantNameSpec extends BaseSpec<Tenant> {}

@Spec(path = "active", params = "active", spec = Equal.class)
interface TenantActiveSpec extends BaseSpec<Tenant> {}

@Spec(path = "name", params = "q", spec = LikeIgnoreCase.class)
interface TenantNameSearchSpec extends BaseSpec<Tenant> {}

public interface TenantSpec extends TenantNameSpec, TenantActiveSpec, TenantNameSearchSpec {}
