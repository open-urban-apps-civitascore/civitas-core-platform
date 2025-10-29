package de.civitascore.portal.repository.specification.base;

import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

/**
 * Base specification for common fields present in BaseEntity.
 *
 * <p>Provides filtering by: - id: exact match (supports comma-separated list) - createdAtFrom:
 * created after date - createdAtTo: created before date - modifiedAtFrom: modified after date -
 * modifiedAtTo: modified before date
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
