package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends BaseRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByExternalId(String externalId);
}
