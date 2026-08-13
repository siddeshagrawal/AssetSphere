package com.assetsphere.modules.processing.consumer.persistence;

import com.assetsphere.modules.processing.consumer.domain.ProcessedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events(event_id, consumer_name, processed_at)
            VALUES (:eventId, :consumerName, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int reserve(@Param("eventId") UUID eventId,
                @Param("consumerName") String consumerName,
                @Param("processedAt") Instant processedAt);
}
