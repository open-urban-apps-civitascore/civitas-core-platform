package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Installation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The journal of installs. The install adds entries; nothing removes them. */
public interface InstallationRepository extends JpaRepository<Installation, UUID> {

  /**
   * Whether this instance has an installation of the package. An instance can have only one
   * installation of a package. The unique index {@code uq_installations_package} also stops the
   * second of two installs that run at the same time.
   */
  boolean existsByPackageId(String packageId);
}
