
package com.mycompany.repository;

import com.mycompany.model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findByTicketId(Long ticketId);

    @Query("SELECT m FROM TicketMessage m WHERE m.ticketId = :ticketId AND (m.senderId = :userId OR m.receiverId = :userId)")
    List<TicketMessage> findByTicketIdAndUserId(@Param("ticketId") Long ticketId, @Param("userId") Long userId);
}