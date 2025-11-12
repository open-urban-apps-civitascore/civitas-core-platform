package de.civitascore.portal.repository.specification.base;

import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Base specification for tenant-aware entities.
 *
 * <p>Adds filtering by:
 *
 * <ul>
 *   <li>tenantId: exact match (usually set automatically by service layer)
 * </ul>
 */
@Spec(path = "tenantId", params = "tenantId", spec = Equal.class)
interface TenantIdSpec<T> extends BaseSpec<T> {}

public interface TenantAwareSpec<T> extends TenantIdSpec<T> {}
