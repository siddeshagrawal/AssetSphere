package com.assetsphere.modules.auth.persistence;

import com.assetsphere.modules.auth.domain.OAuthLoginTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OAuthLoginTicketRepository extends JpaRepository<OAuthLoginTicket, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from OAuthLoginTicket ticket where ticket.ticketHash = :ticketHash")
    Optional<OAuthLoginTicket> findByTicketHashForUpdate(@Param("ticketHash") String ticketHash);
}
