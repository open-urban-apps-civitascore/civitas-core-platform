package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

@Spec(path = "dataStructureStatus", params = "dataStructureStatus", spec = Equal.class)
interface DataStructureStatusSpec extends Specification<DataStructure> {}

public interface DataStructureSpec
    extends NamedEntitySpec<DataStructure>, DataStructureStatusSpec {}
