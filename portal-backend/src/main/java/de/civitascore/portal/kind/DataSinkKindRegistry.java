package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Looks up the {@link DataSinkKind} of each {@link DataSinkType}. The application does not start
 * when a type has no kind or two kinds.
 */
@Component
public class DataSinkKindRegistry {

  private final Map<DataSinkType, DataSinkKind> kinds;

  public DataSinkKindRegistry(Collection<DataSinkKind> kinds) {
    this.kinds = KindIndex.byId(DataSinkType.class, kinds, DataSinkKind::id);
  }

  /** The kind of the given id. */
  public DataSinkKind get(DataSinkType id) {
    return kinds.get(id);
  }
}
