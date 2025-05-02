import React from 'react';
import TicketItem from './TicketItem';

const Sidebar = ({ tickets, selectedTicketId, onSelectTicket }) => {
  return (
    <div className="w-64 bg-light-secondaryBg dark:bg-dark-secondaryBg h-full p-4 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-400">
      <h2 className="text-lg font-semibold mb-4 text-light-text dark:text-dark-text">Tickets</h2>
      {tickets.length === 0 ? (
        <p className="text-gray-500 dark:text-gray-400">No tickets available.</p>
      ) : (
        tickets.map((ticket) => (
          <TicketItem
            key={ticket.id}
            ticket={ticket}
            isSelected={selectedTicketId === ticket.id}
            onSelect={() => onSelectTicket(ticket.id)}
          />
        ))
      )}
    </div>
  );
};

export default Sidebar;
