package de.civitascore.portal.repository.specification.base;

import net.kaczmarzyk.spring.data.jpa.domain.GreaterThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.domain.LessThanOrEqual;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

/**
 * Base specification for common fields present in BaseEntity. All entity specifications should
 * extend this.
 */
@FilterParam(name = "id", description = "Filter by one or more IDs.")
@Spec(path = "id", params = "id", paramSeparator = ',', spec = In.class)
interface BaseIdSpec<T> extends Specification<T> {}

@FilterParam(
    name = "createdAtFrom",
    description = "Filter by creation date greater than or equal to this value (ISO 8601).")
@Spec(path = "createdAt", params = "createdAtFrom", spec = GreaterThanOrEqual.class)
interface BaseCreatedAtFromSpec<T> extends Specification<T> {}

@FilterParam(
    name = "createdAtTo",
    description = "Filter by creation date less than or equal to this value (ISO 8601).")
@Spec(path = "createdAt", params = "createdAtTo", spec = LessThanOrEqual.class)
interface BaseCreatedAtToSpec<T> extends Specification<T> {}

@FilterParam(
    name = "modifiedAtFrom",
    description = "Filter by modification date greater than or equal to this value (ISO 8601).")
@Spec(path = "modifiedAt", params = "modifiedAtFrom", spec = GreaterThanOrEqual.class)
interface BaseModifiedAtFromSpec<T> extends Specification<T> {}

@FilterParam(
    name = "modifiedAtTo",
    description = "Filter by modification date less than or equal to this value (ISO 8601).")
@Spec(path = "modifiedAt", params = "modifiedAtTo", spec = LessThanOrEqual.class)
interface BaseModifiedAtToSpec<T> extends Specification<T> {}

public interface BaseSpec<T>
    extends BaseIdSpec<T>,
        BaseCreatedAtFromSpec<T>,
        BaseCreatedAtToSpec<T>,
        BaseModifiedAtFromSpec<T>,
        BaseModifiedAtToSpec<T> {}
