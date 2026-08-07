package com.assetsphere.modules.processing.outbox.persistence;

import com.assetsphere.modules.processing.outbox.domain.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
