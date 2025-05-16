import React, { useState } from 'react';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import ChatWindow from '../components/ChatWindow';

const Dashboard = () => {
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [tickets, setTickets] = useState([]);

 
  const handleNewMessage = (ticketId) => {
    if (selectedTicket?.id !== ticketId) {
      setTickets((prevTickets) => {
        const updatedTickets = prevTickets.map((ticket) =>
          ticket.id === ticketId
            ? { ...ticket, unreadCount: (ticket.unreadCount || 0) + 1 }
            : ticket
        );
   
        const ticketIndex = updatedTickets.findIndex((ticket) => ticket.id === ticketId);
        if (ticketIndex !== -1) {
          const [movedTicket] = updatedTickets.splice(ticketIndex, 1);
          return [movedTicket, ...updatedTickets];
        }
        return updatedTickets;
      });
    }
  };


  const onSelectTicket = (ticket) => {
    setSelectedTicket(ticket);
    setTickets((prevTickets) =>
      prevTickets.map((t) =>
        t.id === ticket.id ? { ...t, unreadCount: 0 } : t
      )
    );
  };

  return (
    <div className="flex flex-col h-screen bg-light-bg dark:bg-dark-bg">
      <Header />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar
          selectedTicketId={selectedTicket?.id}
          onSelectTicket={onSelectTicket}
          tickets={tickets}
          setTickets={setTickets}
        />
        <ChatWindow
          ticket={selectedTicket}
          onNewMessage={handleNewMessage}
        />
      </div>
    </div>
  );
};

export default Dashboard;