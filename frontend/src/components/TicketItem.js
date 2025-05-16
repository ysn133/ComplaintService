import React, { useState, useEffect } from 'react';

const TicketItem = ({ ticket, isSelected, onSelect }) => {
  const [unreadCount, setUnreadCount] = useState(ticket.unreadCount || 0);

  useEffect(() => {

    setUnreadCount(ticket.unreadCount || 0);
  }, [ticket.unreadCount]);


  useEffect(() => {
    if (!isSelected) {

      setUnreadCount(ticket.unreadCount || 0);
    }
  }, [isSelected, ticket.unreadCount]);

  return (
    <div
      onClick={onSelect}
      className={`p-3 mb-2 rounded-lg cursor-pointer transition-all duration-200 shadow-sm ${
        isSelected
          ? 'bg-primary text-white'
          : 'bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text hover:bg-gray-200 dark:hover:bg-gray-600'
      }`}
    >
      <div className="flex justify-between items-center">
        <div>
          <h3 className="font-semibold">Ticket #{ticket.id}</h3>
          <p className="text-sm truncate">{ticket.subject}</p>
        </div>
        {unreadCount > 0 && (
          <span className="bg-accent text-white text-xs rounded-full px-2 py-1">
            {unreadCount}
          </span>
        )}
      </div>
      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{ticket.lastMessageTime}</p>
    </div>
  );
};

export default TicketItem;