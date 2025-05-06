package com.mycompany.repository;

import com.mycompany.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByClientId(Long clientId);
    List<Ticket> findBySupportTeamId(Long supportTeamId);
    long countBySupportTeamIdAndStatusNotIn(Long supportTeamId, List<String> statuses);
}