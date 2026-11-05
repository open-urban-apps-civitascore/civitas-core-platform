package de.civitascore.portal.security;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelReference;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.PublishedStructureRepository;
import de.civitascore.portal.util.ModelReferenceNotAvailableException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Decides whether a data structure model may reference models of other data structures, which OPA
 * cannot see. Allowed are published structures and data structures the caller may read; an
 * embedding reference must name a version. Every refusal reads the same; the reason is logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ModelReferenceAuthorizer {

  private final ModelRegistryGateway modelRegistryGateway;
  private final DataStructureRepository dataStructureRepository;
  private final PublishedStructureRepository publishedStructureRepository;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;

  /**
   * Refuses a model about to be stored that adds a reference the caller may not use. References the
   * stored model already holds are not checked again.
   *
   * @param dataStructureId the id of the data structure the model belongs to
   * @param model the submitted model
   * @param styles the submitted styles
   * @param storedModelUrn the version's stored model, or null when it has none yet
   * @throws ModelReferenceNotAvailableException when an added reference may not be used
   */
  public void authorizeWrite(
      UUID dataStructureId,
      Map<String, Object> model,
      Map<String, Object> styles,
      String storedModelUrn) {
    requireUsable(
        dataStructureId,
        modelRegistryGateway.addedForeignReferences(
            dataStructureId, model, styles, storedModelUrn));
  }

  /**
   * Refuses a stored model that holds a reference the caller may not use.
   *
   * @param dataStructureId the id of the data structure the model belongs to
   * @param modelUrn CORE URN of the stored model
   * @throws ModelReferenceNotAvailableException when a reference may not be used
   * @throws de.civitascore.portal.util.ModelMissingException when the registry holds no such model
   */
  public void authorizeStored(UUID dataStructureId, String modelUrn) {
    requireUsable(
        dataStructureId, modelRegistryGateway.foreignReferencesOf(dataStructureId, modelUrn));
  }

  private void requireUsable(UUID dataStructureId, Collection<ModelReference> references) {
    Set<UUID> toRead = new LinkedHashSet<>();
    for (ModelReference reference : references) {
      if (reference.kind() == ModelReference.Kind.EMBEDDED_FLOATING) {
        throw refused(dataStructureId, reference.target(), "embedded without a version");
      }
      if (reference.mintedBy() == null) {
        throw refused(dataStructureId, reference.target(), "no data structure minted it");
      }
      if (publishedStructureRepository.existsByLogicalUrn(reference.mintedBy())) {
        continue;
      }
      UUID owner =
          modelRegistryGateway
              .modelIdOf(reference.mintedBy())
              .filter(dataStructureRepository::existsById)
              .orElseThrow(
                  () ->
                      refused(
                          dataStructureId,
                          reference.target(),
                          "minted by no portal data structure"));
      toRead.add(owner);
    }
    try {
      scopeAccessAuthorizer.authorizeReadAccess(ScopeType.DATASTRUCTURE, toRead);
    } catch (AccessDeniedException e) {
      throw refused(dataStructureId, toRead.toString(), "not readable by the caller");
    }
  }

  private static ModelReferenceNotAvailableException refused(
      UUID dataStructureId, String target, String reason) {
    log.warn(
        "Model reference of data structure {} refused: {} ({})",
        dataStructureId,
        Encode.forJava(target),
        reason);
    return new ModelReferenceNotAvailableException();
  }
}
