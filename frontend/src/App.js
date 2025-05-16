import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import SupportDashboard from './support/SupportDashboard';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/support" element={<SupportDashboard />} />
        <Route path="/profile" element={<div className="p-4 bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text">Profile Page (TBD)</div>} />
        <Route path="/settings" element={<div className="p-4 bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text">Settings Page (TBD)</div>} />
        <Route path="/logout" element={<div className="p-4 bg-light-bg dark:bg-dark-bg text-light-text dark:text-dark-text">Logout Page (TBD)</div>} />
      </Routes>
    </Router>
  );
}

export default App;
