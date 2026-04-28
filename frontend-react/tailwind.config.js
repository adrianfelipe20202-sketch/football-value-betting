/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          green: '#00e676',
          red: '#ff1744',
          gold: '#ffd600',
          blue: '#2979ff',
        }
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'glow': 'glow 2s ease-in-out infinite alternate',
      },
      keyframes: {
        glow: {
          '0%': { boxShadow: '0 0 5px rgba(0, 230, 118, 0.3)' },
          '100%': { boxShadow: '0 0 20px rgba(0, 230, 118, 0.6)' },
        }
      }
    },
  },
  plugins: [],
}
