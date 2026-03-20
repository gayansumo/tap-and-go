package au.com.transport.tapngo.repository;

import au.com.transport.tapngo.domain.TapEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TapEventRepository extends JpaRepository<TapEvent, Long> {

    /**
     * Bulk status update — used by OutboxDispatcher after successful publish.
     * Single UPDATE statement instead of N individual saves.
     */
    @Modifying
    @Query("UPDATE TapEvent t SET t.status = 'PUBLISHED' WHERE t.id IN :ids")
    int markAsPublished(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE TapEvent t SET t.status = 'FAILED' WHERE t.id IN :ids")
    int markAsFailed(@Param("ids") List<Long> ids);

    @Query("SELECT t FROM TapEvent t WHERE t.status = 'RECEIVED' ORDER BY t.createdAt")
    List<TapEvent> findUnpublished();
}
