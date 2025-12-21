import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './pages/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
    './app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#2E7D32',
          light: '#4CAF50',
          dark: '#1B5E20',
        },
        secondary: {
          DEFAULT: '#558B2F',
          light: '#7CB342',
          dark: '#33691E',
        },
        tertiary: {
          DEFAULT: '#00796B',
          light: '#26A69A',
          dark: '#004D40',
        },
        background: {
          DEFAULT: '#FDFDF5',
          dark: '#1A1C19',
        },
        surface: {
          DEFAULT: '#FFFFFF',
          muted: '#F5F5F5',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};

export default config;
