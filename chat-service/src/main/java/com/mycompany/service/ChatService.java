package com.mycompany.service;

import com.mycompany.model.TicketMessage;
import com.mycompany.model.TicketMessage.SenderType;
import com.mycompany.repository.TicketMessageRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    /**
     * Saves a new message for a ticket (used by REST API and WebSocket).
     *
     * @param ticketId     The ID of the ticket.
     * @param senderId     The ID of the sender (user or support agent).
     * @param senderType   The type of sender (CLIENT or SUPPORT).
     * @param receiverId   The ID of the receiver (user or support agent).
     * @param receiverType The type of receiver (CLIENT or SUPPORT).
     * @param message      The message content.
     * @return The saved TicketMessage object.
     */
    public TicketMessage saveMessage(Long ticketId, Long senderId, SenderType senderType, Long receiverId, SenderType receiverType, String message) {
        TicketMessage ticketMessage = new TicketMessage();
        ticketMessage.setTicketId(ticketId);
        ticketMessage.setSenderId(senderId);
        ticketMessage.setSenderType(senderType);
        ticketMessage.setReceiverId(receiverId);
        ticketMessage.setReceiverType(receiverType);
        ticketMessage.setMessage(message);
        ticketMessage.setCreatedAt(LocalDateTime.now());
        ticketMessage.setIsRead(false);
        return ticketMessageRepository.save(ticketMessage);
    }

    /**
     * Saves a TicketMessage object (used by WebSocket controller).
     *
     * @param message The TicketMessage object to save.
     * @return The saved TicketMessage object.
     */
    public TicketMessage saveMessage(TicketMessage message) {
        // Ensure createdAt and isRead are set if not already
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        if (message.getIsRead() == null) {
            message.setIsRead(false);
        }
        return ticketMessageRepository.save(message);
    }

    /**
     * Retrieves all messages for a given ticket ID where the user is either the sender or receiver.
     *
     * @param ticketId The ID of the ticket.
     * @param userId   The ID of the user requesting the messages.
     * @return A list of TicketMessage objects for the ticket.
     */
    public List<TicketMessage> getMessagesByTicketId(Long ticketId, Long userId) {
        return ticketMessageRepository.findByTicketIdAndUserId(ticketId, userId);
    }
}