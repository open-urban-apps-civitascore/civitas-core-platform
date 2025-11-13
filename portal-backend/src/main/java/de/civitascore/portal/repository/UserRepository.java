package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends BaseRepository<User, String> {
  Optional<User> findByEmail(String email);

  Optional<User> findByExternalId(String externalId);
}
