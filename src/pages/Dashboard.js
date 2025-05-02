import React, { useState } from 'react';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import ChatWindow from '../components/ChatWindow';

const Dashboard = () => {
  // Mock ticket data since the backend ticket service isn't ready
  const [tickets] = useState([
    {
      id: 1,
      subject: 'Order Issue',
      lastMessageTime: '2025-04-06T02:00:00', // Latest message timestamp
      unreadCount: 2, // 2 unread messages from CLIENT
    },
  ]);
  const [selectedTicketId, setSelectedTicketId] = useState(null);

  const selectedTicket = tickets.find((ticket) => ticket.id === selectedTicketId);

  return (
    <div className="flex flex-col h-screen bg-light-bg dark:bg-dark-bg">
      <Header />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar
          tickets={tickets}
          selectedTicketId={selectedTicketId}
          onSelectTicket={setSelectedTicketId}
        />
        <ChatWindow ticket={selectedTicket} />
      </div>
    </div>
  );
};

export default Dashboard;
