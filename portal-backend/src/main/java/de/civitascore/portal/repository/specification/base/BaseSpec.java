package de.civitascore.portal.repository.specification.base;

import net.kaczmarzyk.spring.data.jpa.domain.GreaterThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.domain.LessThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

/**
 * Base specification for common fields present in BaseEntity.
 *
 * <p>Provides filtering by:
 *
 * <ul>
 *   <li>id: exact match (supports comma-separated list)
 *   <li>createdAtFrom: created after date
 *   <li>createdAtTo: created before date
 *   <li>modifiedAtFrom: modified after date
 *   <li>modifiedAtTo: modified before date
 * </ul>
 *
 * <p>All entity specifications should extend this.
 */
@Spec(path = "id", params = "id", paramSeparator = ',', spec = In.class)
interface BaseIdSpec<T> extends Specification<T> {}

@Spec(path = "createdAt", params = "createdAtFrom", spec = GreaterThanOrEqual.class)
interface BaseCreatedAtFromSpec<T> extends Specification<T> {}

@Spec(path = "createdAt", params = "createdAtTo", spec = LessThanOrEqual.class)
interface BaseCreatedAtToSpec<T> extends Specification<T> {}

@Spec(path = "modifiedAt", params = "modifiedAtFrom", spec = GreaterThanOrEqual.class)
interface BaseModifiedAtFromSpec<T> extends Specification<T> {}

@Spec(path = "modifiedAt", params = "modifiedAtTo", spec = LessThanOrEqual.class)
interface BaseModifiedAtToSpec<T> extends Specification<T> {}

public interface BaseSpec<T>
    extends BaseIdSpec<T>,
        BaseCreatedAtFromSpec<T>,
        BaseCreatedAtToSpec<T>,
        BaseModifiedAtFromSpec<T>,
        BaseModifiedAtToSpec<T> {}
