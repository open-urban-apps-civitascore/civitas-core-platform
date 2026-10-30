package de.civitascore.portal.kind;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.entity.NamedApi;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Looks up the {@link ApiKind} of each {@link ApiStandard}. The application does not start when a
 * standard has no kind or two kinds.
 */
@Component
public class ApiKindRegistry {

  private final Map<ApiStandard, ApiKind> kinds;

  public ApiKindRegistry(Collection<ApiKind> kinds) {
    this.kinds = KindIndex.byId(ApiStandard.class, kinds, ApiKind::id);
  }

  /** The kind of the given id. */
  public ApiKind get(ApiStandard id) {
    return kinds.get(id);
  }

  /** The API among the given ones that the Layers of their Dataset link to. */
  public Optional<NamedApi> layerPublishingApi(Collection<NamedApi> apis) {
    return apis.stream().filter(api -> get(api.getStandard()).publishesLayers()).findFirst();
  }
}
