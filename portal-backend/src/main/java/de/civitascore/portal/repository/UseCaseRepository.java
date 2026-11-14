package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.UseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The journal of installs. The install adds use cases, and an uninstall marks them. Nothing removes
 * them.
 */
public interface UseCaseRepository extends JpaRepository<UseCase, UUID> {

  /**
   * Whether the Data pool has an active use case of the package. A Data pool can have only one
   * active use case of a package. For a package without a Data pool, the Data pool is null, and an
   * instance can have only one active use case of such a package. The unique index {@code
   * uq_use_cases_active_package_datapool} also stops the second of two installs that run at the
   * same time.
   *
   * @param packageId the identity of the package
   * @param datapoolId the Data pool of the use case, or null for a package without a Data pool
   * @return whether such an active use case exists
   */
  default boolean existsActive(String packageId, UUID datapoolId) {
    return datapoolId == null
        ? existsByPackageIdAndDatapoolIdIsNullAndUninstalledAtIsNull(packageId)
        : existsByPackageIdAndDatapoolIdAndUninstalledAtIsNull(packageId, datapoolId);
  }

  boolean existsByPackageIdAndDatapoolIdIsNullAndUninstalledAtIsNull(String packageId);

  boolean existsByPackageIdAndDatapoolIdAndUninstalledAtIsNull(String packageId, UUID datapoolId);

  /**
   * Returns a page of the use cases of a Data pool, the active and the uninstalled ones.
   *
   * @param datapoolId the Data pool
   * @param pageable the page and the sort order
   * @return the use cases of the page
   */
  Page<UseCase> findByDatapoolId(UUID datapoolId, Pageable pageable);

  /**
   * Returns a use case only if it is in the Data pool.
   *
   * @param id the id of the use case
   * @param datapoolId the Data pool
   * @return the use case, or empty if it does not exist or is in another Data pool
   */
  Optional<UseCase> findByIdAndDatapoolId(UUID id, UUID datapoolId);
}
