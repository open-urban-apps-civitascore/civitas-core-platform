package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface DataSetNameSpec extends NamedEntitySpec<DataSet> {}

public interface DataSetSpec extends DataSetNameSpec {}
