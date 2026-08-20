package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Installation;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read/write access to the install provenance records. */
public interface InstallationRepository extends BaseRepository<Installation, UUID> {

  /**
   * How many OTHER active installations reference the artifact with this URN — the reference count
   * uninstall decides by. Uninstalled installations do not count: their claim on the artifact
   * ended. This is the query the journal design exists for; it answers "does anyone else still need
   * this?" without any foreign key onto the artifact itself.
   */
  @Query(
      "select count(a) from Installation i join i.artifacts a"
          + " where a.urn = :urn and i.id <> :installationId and i.uninstalledAt is null")
  long countOtherActiveInstallationsReferencing(
      @Param("urn") String urn, @Param("installationId") UUID installationId);
}
