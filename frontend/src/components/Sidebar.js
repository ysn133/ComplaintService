import React, { useState, useEffect } from 'react';
import axios from 'axios';
import TicketItem from './TicketItem';

const Sidebar = ({ selectedTicketId, onSelectTicket, tickets, setTickets }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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

  useEffect(() => {
    const fetchTickets = async () => {
      if (!token) {
        console.error('No JWT token available');
        setError('No authentication token provided. Please include a token in the URL.');
        setLoading(false);
        return;
      }

      try {
        console.log('Fetching tickets from API with token:', token);
        const response = await axios.get('https://tickets.prjsdr.xyz/api/tickets', {
          headers: { Authorization: `Bearer ${token}` },
        });

        console.log('API response:', response.data);
        const ticketData = response.data.tickets || [];

        if (!Array.isArray(ticketData)) {
          console.warn('response.data.tickets is not an array:', ticketData);
          setError('Invalid response format from server. Expected an array of tickets.');
          setTickets([]);
          setLoading(false);
          return;
        }

        const validTickets = ticketData
          .map((ticket) => ({
            id: ticket.id,
            subject: ticket.title,
            lastMessageTime: ticket.updatedAt,
            unreadCount: ticket.unreadCount || 0,
            supportTeamId: ticket.supportTeamId,
          }))
          .filter(
            (ticket) => ticket && typeof ticket === 'object' && ticket.id && ticket.subject && ticket.supportTeamId
          );

        if (validTickets.length !== ticketData.length) {
          console.warn('Some tickets are invalid:', ticketData);
        }

        setTickets(validTickets);
        setLoading(false);
      } catch (err) {
        console.error('Error fetching tickets:', err);
        console.error('Error details:', {
          message: err.message,
          response: err.response
            ? {
                status: err.response.status,
                data: err.response.data,
                headers: err.response.headers,
              }
            : 'No response received',
        });

        let errorMessage = 'Failed to fetch tickets. Please try again later.';
        if (err.response) {
          if (err.response.status === 401) {
            errorMessage = 'Authentication failed. Please check your token.';
          } else if (err.response.status === 403) {
            errorMessage = 'Access denied. You do not have permission to view tickets.';
          } else if (err.response.status === 404) {
            errorMessage = 'Tickets endpoint not found. Please check the API URL.';
          }
        } else if (err.code === 'ERR_NETWORK') {
          errorMessage = 'Network error. Please check if the server is running and CORS is configured.';
        }

        setError(errorMessage);
        setTickets([]);
        setLoading(false);
      }
    };

    fetchTickets();
  }, [token, setTickets]);

  const handleTicketSelect = (ticket) => {
    console.log('Sidebar: Selected ticket:', ticket);
    onSelectTicket(ticket);
  };

  return (
    <div className="w-64 bg-light-secondaryBg dark:bg-dark-secondaryBg h-full p-4 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-400">
      <h2 className="text-lg font-semibold mb-4 text-light-text dark:text-dark-text">Tickets</h2>
      {loading ? (
        <p className="text-gray-500 dark:text-gray-400">Loading tickets...</p>
      ) : error ? (
        <p className="text-red-500">{error}</p>
      ) : tickets.length === 0 ? (
        <p className="text-gray-500 dark:text-gray-400">No tickets available.</p>
      ) : (
        tickets.map((ticket) => (
          <TicketItem
            key={ticket.id}
            ticket={ticket}
            isSelected={selectedTicketId === ticket.id}
            onSelect={() => handleTicketSelect(ticket)}
          />
        ))
      )}
    </div>
  );
};

export default Sidebar;
