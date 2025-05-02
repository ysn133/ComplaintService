import React, { useState } from 'react';
import SupportHeader from './SupportHeader';
import SupportSidebar from './SupportSidebar';
import SupportChatWindow from './SupportChatWindow';

const SupportDashboard = () => {
  const [tickets] = useState([
    {
      id: 1,
      subject: 'Order Issue',
      category: 'Billing',
      priority: 'High',
      status: 'Open',
      lastMessageTime: '2025-04-06T02:00:00',
      unreadCount: 2,
    },
    {
      id: 2,
      subject: 'Website Bug',
      category: 'Technical',
      priority: 'Medium',
      status: 'In Progress',
      lastMessageTime: '2025-04-05T10:00:00',
      unreadCount: 0,
    },
  ]);
  const [selectedTicketId, setSelectedTicketId] = useState(null);

  const selectedTicket = tickets.find((ticket) => ticket.id === selectedTicketId);

  return (
    <div className="flex flex-col h-screen bg-gray-100 dark:bg-gray-800">
      <SupportHeader />
      <div className="flex flex-1 overflow-hidden">
        <SupportSidebar
          tickets={tickets}
          selectedTicketId={selectedTicketId}
          onSelectTicket={setSelectedTicketId}
        />
        <SupportChatWindow ticket={selectedTicket} />
      </div>
    </div>
  );
};

export default SupportDashboard;
