package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Installation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** The install journal. Append-only: entries are written once and never edited. */
public interface InstallationRepository extends JpaRepository<Installation, UUID> {

  /**
   * Whether this package is already installed here. A package fixes the URNs of its artifacts, so
   * installing it twice on one instance would collide with itself.
   */
  boolean existsByPackageId(String packageId);

  Page<Installation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
