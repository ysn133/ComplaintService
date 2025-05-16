import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

const Header = () => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(() => {
    const savedMode = localStorage.getItem('darkMode');
    return savedMode ? JSON.parse(savedMode) : window.matchMedia('(prefers-color-scheme: dark)').matches;
  });

  useEffect(() => {
    if (isDarkMode) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
    localStorage.setItem('darkMode', JSON.stringify(isDarkMode));
  }, [isDarkMode]);

  const toggleDarkMode = () => {
    setIsDarkMode((prevMode) => !prevMode);
  };

  return (
    <header className="bg-primary text-white p-4 flex justify-between items-center shadow-lg">
      <Link to="/" className="text-xl font-semibold">Complaint Service</Link>
      <div className="flex items-center space-x-4">
        <button
          onClick={toggleDarkMode}
          className="p-2 rounded-full hover:bg-blue-700 transition"
          aria-label="Toggle Dark Mode"
        >
          {isDarkMode ? '☀️' : '🌙'}
        </button>
        <div className="relative">
          <button
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className="flex items-center space-x-2 focus:outline-none"
          >
            <img
              src="/avatar-client.png"
              alt="User Avatar"
              className="w-8 h-8 rounded-full border-2 border-white"
            />
            <span className="font-medium">User</span>
            <svg
              className={`w-4 h-4 transform ${isDropdownOpen ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          {isDropdownOpen && (
            <div className="absolute right-0 mt-2 w-48 bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text rounded-lg shadow-lg z-10">
              <Link
                to="/profile"
                className="block px-4 py-2 hover:bg-light-secondaryBg dark:hover:bg-dark-secondaryBg"
                onClick={() => setIsDropdownOpen(false)}
              >
                Profile
              </Link>
              <Link
                to="/settings"
                className="block px-4 py-2 hover:bg-light-secondaryBg dark:hover:bg-dark-secondaryBg"
                onClick={() => setIsDropdownOpen(false)}
              >
                Settings
              </Link>
              <Link
                to="/logout"
                className="block px-4 py-2 hover:bg-light-secondaryBg dark:hover:bg-dark-secondaryBg"
                onClick={() => setIsDropdownOpen(false)}
              >
                Logout
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Header;
