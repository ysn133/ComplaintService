import React, { useState } from 'react';
import SupportTicketItem from './SupportTicketItem';

const SupportSidebar = ({ tickets, selectedTicketId, onSelectTicket, onMarkAsRead }) => {
  const [categoryFilter, setCategoryFilter] = useState('All');
  const [priorityFilter, setPriorityFilter] = useState('All');
  const [timeFilter, setTimeFilter] = useState('Newest');

  const categories = ['All', 'Technical', 'Billing', 'General'];
  const priorities = ['All', 'Low', 'Medium', 'High'];
  const times = ['Newest', 'Oldest'];

  const filteredTickets = tickets
    .filter((ticket) => categoryFilter === 'All' || ticket.category === categoryFilter)
    .filter((ticket) => priorityFilter === 'All' || ticket.priority === priorityFilter)
    .sort((a, b) => {
      if (a.isNew && !b.isNew) return -1;
      if (!a.isNew && b.isNew) return 1;
      const dateA = new Date(a.lastMessageTime || '1970-01-01');
      const dateB = new Date(b.lastMessageTime || '1970-01-01');
      return timeFilter === 'Newest' ? dateB - dateA : dateA - dateB;
    });

  console.log('Sidebar: Sorted tickets:', filteredTickets.map(t => ({ id: t.id, isNew: t.isNew })));

  return (
    <div className="w-80 bg-gray-200 dark:bg-gray-700 h-full p-4 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-400">
      <h2 className="text-lg font-semibold mb-4 text-gray-800 dark:text-gray-200">Support Tickets</h2>
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Category</label>
        <div className="flex flex-wrap gap-2">
          {categories.map((category) => (
            <button
              key={category}
              onClick={() => setCategoryFilter(category)}
              className={`px-3 py-1 rounded-full text-sm font-medium transition-all duration-200 ${
                categoryFilter === category
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-300 dark:bg-gray-600 text-gray-800 dark:text-gray-200 hover:bg-gray-400 dark:hover:bg-gray-500'
              }`}
            >
              {category}
            </button>
          ))}
        </div>
      </div>
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Priority</label>
        <div className="flex flex-wrap gap-2">
          {priorities.map((priority) => (
            <button
              key={priority}
              onClick={() => setPriorityFilter(priority)}
              className={`px-3 py-1 rounded-full text-sm font-medium transition-all duration-200 ${
                priorityFilter === priority
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-300 dark:bg-gray-600 text-gray-800 dark:text-gray-200 hover:bg-gray-400 dark:hover:bg-gray-500'
              }`}
            >
              {priority}
            </button>
          ))}
        </div>
      </div>
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Sort by Time</label>
        <div className="flex flex-wrap gap-2">
          {times.map((time) => (
            <button
              key={time}
              onClick={() => setTimeFilter(time)}
              className={`px-3 py-1 rounded-full text-sm font-medium transition-all duration-200 ${
                timeFilter === time
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-300 dark:bg-gray-600 text-gray-800 dark:text-gray-200 hover:bg-gray-400 dark:hover:bg-gray-500'
              }`}
            >
              {time}
            </button>
          ))}
        </div>
      </div>
      {filteredTickets.length === 0 ? (
        <p className="text-gray-500 dark:text-gray-400">No tickets available.</p>
      ) : (
        filteredTickets.map((ticket) => (
          <SupportTicketItem
            key={ticket.id}
            ticket={ticket}
            isSelected={selectedTicketId === ticket.id}
            onSelect={() => onSelectTicket(ticket.id)}
            onMarkAsRead={onMarkAsRead}
          />
        ))
      )}
    </div>
  );
};

export default SupportSidebar;