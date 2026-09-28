package de.civitascore.portal.frost;

import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import de.civitascore.portal.model.entity.PublishedStructure;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.PublishedStructureRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts the structure of every port into the registry, so that it is an artifact with an identity
 * instead of a file on the class path.
 *
 * <p>An artifact is what an import can name. A Data structure that takes a port structure over pins
 * the version this registrar stored, and a later version of the port structure then leaves that
 * Data structure alone.
 *
 * <p>It runs on every start and writes only when the declaration changed. The decision is made on
 * the hash of the rendered document, not on the stored artifact: the registry splits the document
 * into its member Elements and stamps its own self-description on it, so the two are not
 * comparable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(PublishedStructureRegistrar.ORDER)
public class PublishedStructureRegistrar implements ApplicationRunner {

  /** After the permission and role initializers, beside the other FROST start-up work. */
  static final int ORDER = 110;

  private final ModelRegistryGateway modelRegistryGateway;
  private final PublishedStructureRepository publishedStructureRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    int published = 0;
    for (PortStructure structure : PortStructureCatalog.all()) {
      if (publish(structure)) {
        published++;
      }
    }
    if (published > 0) {
      log.info("Published {} FROST port structure(s) to the model registry", published);
    }
  }

  /** Stores one structure. Answers whether it wrote anything. */
  private boolean publish(PortStructure structure) {
    String rendered = PortStructureGenerator.render(structure);
    String hash = sha256(rendered);
    Optional<PublishedStructure> stored = publishedStructureRepository.findByPort(structure.port());
    if (stored.filter(pin -> hash.equals(pin.getContentHash())).isPresent()) {
      return false;
    }

    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storeModel(
            stored.map(PublishedStructure::getLogicalUrn),
            structure.port(),
            PortStructureGenerator.model(structure),
            // A published structure carries no diagram. Nobody draws it, and a version without one
            // is a version a data source produces too.
            null,
            // The first store begins the history; a changed declaration revises it inside the same
            // major, because the shape of a port does not turn into another port.
            stored.isPresent() ? VersionBump.MINOR : VersionBump.MAJOR,
            null);

    PublishedStructure entity = stored.orElseGet(PublishedStructure::new);
    entity.setPort(structure.port());
    entity.setLogicalUrn(pin.logicalUrn());
    entity.setVersionedUrn(pin.versionedUrn());
    entity.setContentHash(hash);
    publishedStructureRepository.save(entity);
    return true;
  }

  private static String sha256(String document) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(document.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
