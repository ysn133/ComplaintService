import React from 'react';

const SupportTicketItem = ({ ticket, isSelected, onSelect }) => {
  return (
    <div
      onClick={onSelect}
      className={`p-3 mb-2 rounded-lg cursor-pointer transition-all duration-200 shadow-sm ${
        isSelected
          ? 'bg-blue-600 text-white'
          : 'bg-white dark:bg-gray-600 text-gray-800 dark:text-gray-200 hover:bg-gray-300 dark:hover:bg-gray-500'
      }`}
    >
      <div className="flex justify-between items-center">
        <div>
          <h3 className="font-semibold">Ticket #{ticket.id}</h3>
          <p className="text-sm truncate">{ticket.subject}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">Category: {ticket.category}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">Priority: {ticket.priority}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">Status: {ticket.status}</p>
        </div>
        {ticket.unreadCount > 0 && (
          <span className="bg-red-500 text-white text-xs rounded-full px-2 py-1">
            {ticket.unreadCount}
          </span>
        )}
      </div>
      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{ticket.lastMessageTime}</p>
    </div>
  );
};

export default SupportTicketItem;
