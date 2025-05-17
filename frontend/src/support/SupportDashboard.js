import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SupportHeader from './SupportHeader';
import SupportSidebar from './SupportSidebar';
import SupportChatWindow from './SupportChatWindow';

const SupportDashboard = () => {
  const [tickets, setTickets] = useState([]);
  const [selectedTicketId, setSelectedTicketId] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  // Retrieve token from URL or localStorage
  const getToken = () => {
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');
    if (tokenFromUrl) {
      localStorage.setItem('jwtToken', tokenFromUrl);
      return tokenFromUrl;
    }
    return localStorage.getItem('jwtToken') || null;
  };

  const token = getToken();

  // Fetch tickets from API
  useEffect(() => {
    const fetchTickets = async () => {
      if (!token) {
        setError('No authentication token provided.');
        setIsLoading(false);
        return;
      }

      try {
        const response = await axios.get('https://tickets.prjsdr.xyz/api/tickets', {
          headers: { Authorization: `Bearer ${token}` },
        });
        console.log('SupportDashboard: API response for tickets:', response.data);
        const ticketsData = Array.isArray(response.data.tickets) ? response.data.tickets.map(ticket => ({
          id: ticket.id,
          subject: ticket.title || ticket.subject || 'No Subject',
          category: ticket.categoryId === 1 ? 'Technical' : ticket.categoryId === 2 ? 'Billing' : 'General',
          priority: ticket.priority || 'Low',
          status: ticket.status || 'Open',
          unreadCount: ticket.messageCount || 0,
          lastMessageTime: ticket.lastMessageTime || ticket.createdAt || new Date().toISOString(),
          isNew: ticket.isNew || false,
        })) : [];
        console.log('SupportDashboard: Parsed tickets:', ticketsData);
        setTickets(ticketsData);
        setIsLoading(false);
      } catch (err) {
        console.error('SupportDashboard: Error fetching tickets:', err);
        setError('Failed to load tickets. Please try again later.');
        setIsLoading(false);
      }
    };

    fetchTickets();
  }, [token]);

  // Handle new ticket received from SupportChatWindow
  const handleTicketReceived = (newTicket) => {
    console.log('SupportDashboard: Received new ticket:', newTicket);
    setTickets((prev) => {
      if (prev.some((t) => t.id === newTicket.id)) {
        console.log('SupportDashboard: Ticket already exists, skipping:', newTicket.id);
        return prev;
      }
      const updatedTickets = [newTicket, ...prev];
      console.log('SupportDashboard: Updated tickets:', updatedTickets);
      return updatedTickets;
    });
  };

  // Handle new message for unselected ticket
  const handleNewMessage = (ticketId) => {
    console.log('SupportDashboard: New message for ticket ID:', ticketId);
    setTickets((prevTickets) =>
      prevTickets.map((ticket) =>
        ticket.id === ticketId
          ? { ...ticket, unreadCount: (ticket.unreadCount || 0) + 1, lastMessageTime: new Date().toISOString() }
          : ticket
      )
    );
  };

  // Handle marking ticket as read
  const handleMarkAsRead = (ticketId) => {
    console.log('SupportDashboard: Marking ticket as read:', ticketId);
    setTickets((prevTickets) =>
      prevTickets.map((ticket) =>
        ticket.id === ticketId ? { ...ticket, isNew: false, unreadCount: 0 } : ticket
      )
    );
  };

  const selectedTicket = tickets.find((ticket) => ticket.id === selectedTicketId);

  if (!token) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <p className="text-red-500">No authentication token provided. Please include a token in the URL.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-gray-100 dark:bg-gray-800">
      <SupportHeader />
      <div className="flex flex-1 overflow-hidden">
        {isLoading ? (
          <div className="w-80 bg-gray-200 dark:bg-gray-700 h-full p-4 flex items-center justify-center">
            <p className="text-gray-500 dark:text-gray-400">Loading tickets...</p>
          </div>
        ) : error ? (
          <div className="w-80 bg-gray-200 dark:bg-gray-700 h-full p-4 flex items-center justify-center">
            <p className="text-red-500">{error}</p>
          </div>
        ) : (
          <SupportSidebar
            tickets={tickets}
            selectedTicketId={selectedTicketId}
            onSelectTicket={setSelectedTicketId}
            onMarkAsRead={handleMarkAsRead}
          />
        )}
        <SupportChatWindow
          ticket={selectedTicket}
          onTicketReceived={handleTicketReceived}
          onMarkAsRead={handleMarkAsRead}
          onNewMessage={handleNewMessage}
        />
      </div>
    </div>
  );
};

export default SupportDashboard;