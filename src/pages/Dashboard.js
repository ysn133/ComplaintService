import React, { useState } from 'react';
import Header from '../components/Header';
import Sidebar from '../components/Sidebar';
import ChatWindow from '../components/ChatWindow';

const Dashboard = () => {
  const [selectedTicket, setSelectedTicket] = useState(null);

  return (
    <div className="flex flex-col h-screen bg-light-bg dark:bg-dark-bg">
      <Header />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar
          selectedTicketId={selectedTicket?.id}
          onSelectTicket={setSelectedTicket}
        />
        <ChatWindow ticket={selectedTicket} />
      </div>
    </div>
  );
};

export default Dashboard;
