package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Installation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The install journal. An entry is written by the install and never removed. An uninstall only sets
 * the time of the uninstall on it.
 */
public interface InstallationRepository extends JpaRepository<Installation, UUID> {

  /**
   * Whether this package has an active installation here. A package fixes the URNs of its
   * artifacts, so installing it twice on one instance would collide with itself. An installation
   * that was uninstalled does not count.
   */
  boolean existsByPackageIdAndUninstalledAtIsNull(String packageId);

  Page<Installation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
