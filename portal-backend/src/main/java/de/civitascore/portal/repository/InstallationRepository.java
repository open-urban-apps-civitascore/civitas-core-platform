package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Installation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The journal of installs. The install adds entries, and an uninstall marks them. Nothing removes
 * them.
 */
public interface InstallationRepository extends JpaRepository<Installation, UUID> {

  /**
   * Whether the Data pool has an active installation of the package. A Data pool can have only one
   * active installation of a package. For a package without a Data pool, the Data pool is null, and
   * an instance can have only one active installation of such a package. The unique index {@code
   * uq_installations_active_package_datapool} also stops the second of two installs that run at the
   * same time.
   *
   * @param packageId the identity of the package
   * @param datapoolId the Data pool of the installation, or null for a package without a Data pool
   * @return whether such an active installation exists
   */
  default boolean existsActive(String packageId, UUID datapoolId) {
    return datapoolId == null
        ? existsByPackageIdAndDatapoolIdIsNullAndUninstalledAtIsNull(packageId)
        : existsByPackageIdAndDatapoolIdAndUninstalledAtIsNull(packageId, datapoolId);
  }

  boolean existsByPackageIdAndDatapoolIdIsNullAndUninstalledAtIsNull(String packageId);

  boolean existsByPackageIdAndDatapoolIdAndUninstalledAtIsNull(String packageId, UUID datapoolId);
}
