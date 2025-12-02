package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.OutboxStatus;
import de.civitascore.portal.model.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends BaseRepository<OutboxEvent, UUID> {

  /**
   * Fetches pending events with pessimistic write lock to prevent duplicate processing in
   * multi-instance deployments.
   *
   * <p>The lock ensures that once an instance reads an event, other instances cannot read it until
   * the transaction commits.
   *
   * @param status the status to filter by
   * @param pageable pagination to limit batch size
   * @return list of events with given status (locked)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
  List<OutboxEvent> findByStatusWithLock(@Param("status") OutboxStatus status, Pageable pageable);
}
