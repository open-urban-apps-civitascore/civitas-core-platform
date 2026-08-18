package de.civitascore.portal.service;

import de.civitascore.portal.model.input.MappingImportInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a bundled mapping by its authored identity: the URN is unknown (create), installed with
 * identical content (reuse), or installed with different content (conflict) — the same turnstile
 * {@link DataStructureImportService#importOrReuse} applies to structures.
 *
 * <p>Why the bundle must bring the URN: Model Forge owns identity on <em>create</em> and mints a
 * random disambiguator, discarding any id the caller sent. Storing under an author-chosen URN is
 * the only way a re-installed bundle resolves to the same mapping instead of adding a duplicate.
 * Writing to a URN that does not exist yet inserts the artifact at its initial version — the same
 * generic write path a follow-up version takes.
 *
 * <p>Bundle semantics only. The mapping's references to data structures are validated by the
 * orchestrating {@link DataSetImportService}, which is the only place that knows what else this
 * bundle ships.
 */
@Service
@RequiredArgsConstructor
public class MappingImportService {

  private final MappingService mappingService;
  private final ModelRegistryGateway modelRegistryGateway;

  /**
   * A bundle-import resolution for one mapping: the logical URN it is stored under, the versioned
   * URN of the resolved version — what pipeline nodes pin and the provenance records — and whether
   * an identical mapping was already installed ({@code reused}) or this call created it.
   */
  public record MappingResolution(String logicalUrn, String versionedUrn, boolean reused) {}

  /**
   * Stores the mapping under its authored URN, or resolves it to the already installed one.
   *
   * @param input one bundled mapping
   * @return the logical URN to reference, flagged whether it was reused or created
   * @throws InvalidInputException if {@code mappingUrn} is not a CORE URN of artifact type {@code
   *     mapping} (400)
   * @throws UniqueConstraintViolationException if the identity is installed with different content
   *     (409)
   */
  @Transactional
  public MappingResolution importOrReuse(MappingImportInputDTO input) {
    String logicalUrn = requireMappingUrn(input);

    if (mappingService.exists(logicalUrn)) {
      if (!mappingService.isUnchanged(logicalUrn, input.getDocument())) {
        throw new UniqueConstraintViolationException(
            ("A mapping for '%s' is already installed with different content. Updating an existing"
                    + " installation is not supported yet.")
                .formatted(logicalUrn));
      }
      // No registry write at all: storing an unchanged document would still bump the artifact's
      // version and make a re-install look like an edit.
      return new MappingResolution(
          logicalUrn, mappingService.currentVersionedUrn(logicalUrn).orElse(logicalUrn), true);
    }

    ModelRegistryGateway.ModelPin pin = mappingService.store(logicalUrn, input.getDocument());
    return new MappingResolution(pin.logicalUrn(), pin.versionedUrn(), false);
  }

  /**
   * The authored URN, normalised to its logical form — a caller who pasted a versioned URN is
   * handled rather than rejected, because Model Forge alone assigns versions.
   */
  private String requireMappingUrn(MappingImportInputDTO input) {
    String urn = input.getMappingUrn();
    if (!modelRegistryGateway.isMappingUrn(urn)) {
      throw new InvalidInputException(
          "Mapping",
          input.getName(),
          "mappingUrn must be a CORE URN of artifact type 'mapping'"
              + " (urn:core:<scope>:<owner>:mapping:<domain>:<name>:<disambiguator>), got: "
              + urn);
    }
    return modelRegistryGateway.logicalUrn(urn);
  }
}
