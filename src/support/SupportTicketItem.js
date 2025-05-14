import React from 'react';

const SupportTicketItem = ({ ticket, isSelected, onSelect, onMarkAsRead }) => {
  const handleClick = () => {
    onSelect();
    if (ticket.isNew && onMarkAsRead) {
      onMarkAsRead(ticket.id);
    }
  };

  return (
    <div
      onClick={handleClick}
      className={`p-3 mb-2 rounded-lg cursor-pointer transition-all duration-200 shadow-sm ${
        isSelected
          ? 'bg-blue-600 text-white'
          : 'bg-white dark:bg-gray-600 text-gray-800 dark:text-gray-200 hover:bg-gray-300 dark:hover:bg-gray-500'
      }`}
    >
      <div className="flex justify-between items-center">
        <div className="flex items-center space-x-2">
          {ticket.isNew && !isSelected && (
            <span className="bg-red-500 text-white text-xs font-semibold px-2 py-1 rounded-full animate-pulse">
              New
            </span>
          )}
          <div>
            <h3 className={`font-semibold ${isSelected ? 'text-white' : 'text-gray-800 dark:text-gray-200'}`}>
              Ticket #{ticket.id}
            </h3>
            <p className={`text-sm truncate ${isSelected ? 'text-white' : 'text-gray-800 dark:text-gray-200'}`}>
              {ticket.subject || 'No Subject'}
            </p>
            <p className={`text-xs ${isSelected ? 'text-white' : 'text-gray-500 dark:text-gray-400'}`}>
              Category: {ticket.category || 'Unknown'}
            </p>
            <p className={`text-xs ${isSelected ? 'text-white' : 'text-gray-500 dark:text-gray-400'}`}>
              Priority: {ticket.priority || 'Unknown'}
            </p>
            <p className={`text-xs ${isSelected ? 'text-white' : 'text-gray-500 dark:text-gray-400'}`}>
              Status: {ticket.status || 'Open'}
            </p>
          </div>
        </div>
        {ticket.unreadCount > 0 && (
          <span className="bg-red-500 text-white text-xs rounded-full px-2 py-1">
            {ticket.unreadCount}
          </span>
        )}
      </div>
      <p className={`text-xs ${isSelected ? 'text-white' : 'text-gray-500 dark:text-gray-400'} mt-1`}>
        {ticket.lastMessageTime ? new Date(ticket.lastMessageTime).toLocaleString() : 'No messages'}
      </p>
    </div>
  );
};

export default SupportTicketItem;