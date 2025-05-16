/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  darkMode: 'class', // Ensure dark mode is enabled using the 'dark' class
  theme: {
    extend: {
      colors: {
        primary: '#2563eb', // Blue for buttons, headers
        secondary: '#6b7280', // Gray for secondary elements
        accent: '#10b981', // Green for unread indicators
        light: {
          bg: '#ffffff', // White background
          secondaryBg: '#f3f4f6', // Light gray for sidebar
          text: '#1f2937', // Dark gray text
          message: '#e5e7eb', // Light gray for SUPPORT messages
        },
        dark: {
          bg: '#1f2937', // Dark gray background
          secondaryBg: '#374151', // Darker gray for sidebar
          text: '#d1d5db', // Light gray text
          message: '#4b5563', // Darker gray for SUPPORT messages
        },
      },
    },
  },
  plugins: [],
};
