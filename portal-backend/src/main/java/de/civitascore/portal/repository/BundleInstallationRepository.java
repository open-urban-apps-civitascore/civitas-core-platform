package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.BundleInstallation;
import java.util.UUID;

/** Read/write access to the install provenance records. */
public interface BundleInstallationRepository extends BaseRepository<BundleInstallation, UUID> {}
