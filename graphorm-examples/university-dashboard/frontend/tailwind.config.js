/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Technical Studio & GitHub Dark Neutral Charcoal
        charcoal: {
          canvas: '#0D1117',
          surface: '#161B22',
          elevated: '#21262D',
          border: '#30363D',
        },
        // Mapped ink colors to GitHub Dark tokens
        ink: {
          950: '#0D1117',
          900: '#161B22',
          850: '#161B22',
          800: '#21262D',
          750: '#30363D',
          700: '#30363D',
          600: '#484F58',
          500: '#6E7681',
          400: '#8B949E',
        },
        // Mapped gold / amber
        gold: {
          50: '#FFFBEB',
          100: '#FEF3C7',
          200: '#FDE68A',
          300: '#FCD34D',
          400: '#F59E0B',
          500: '#F59E0B',
          600: '#D97706',
          700: '#B45309',
        },
        // Mapped cobalt / sky
        cobalt: {
          50: '#F0F9FF',
          100: '#E0F2FE',
          200: '#BAE6FD',
          300: '#7DD3FC',
          400: '#38BDF8',
          500: '#38BDF8',
          600: '#0284C7',
          700: '#0369A1',
        },
        // Mapped laurel / emerald
        laurel: {
          50: '#ECFDF5',
          100: '#D1FAE5',
          200: '#A7F3D0',
          300: '#6EE7B7',
          400: '#10B981',
          500: '#10B981',
          600: '#059669',
        },
        // Deep obsidian slate
        slate: {
          950: '#06090f',
          900: '#090d16',
          850: '#0e1422',
          800: '#141c2e',
          750: '#1a243a',
          700: '#23304b',
          600: '#334466',
          500: '#475d85',
        },
        // Sky Blue primary accent
        brand: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#38bdf8',
          600: '#0284c7',
          700: '#0369a1',
        },
        // Vibrant Cyan / Telemetry
        cyan: {
          50: '#ecfeff',
          100: '#cffafe',
          200: '#a5f3fc',
          300: '#67e8f9',
          400: '#22d3ee',
          500: '#06b6d4',
          600: '#0891b2',
          700: '#0e7490',
          800: '#155e75',
          900: '#164e63',
          950: '#083344',
        },
        // Emerald Mint / DB Invariants
        emerald: {
          50: '#ecfdf5',
          100: '#d1fae5',
          200: '#a7f3d0',
          300: '#6ee7b7',
          400: '#34d399',
          500: '#10b981',
          600: '#059669',
          700: '#047857',
          800: '#065f46',
          900: '#064e3b',
        },
        // Coral Rose / Auth & Drop
        rose: {
          50: '#fff1f2',
          100: '#ffe4e6',
          200: '#fecdd3',
          300: '#fda4af',
          400: '#fb7185',
          500: '#f43f5e',
          600: '#e11d48',
          700: '#be123c',
          800: '#9f1239',
          900: '#881337',
        },
        // Amber Gold / Highlights
        amber: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b',
          600: '#d97706',
          700: '#b45309',
          800: '#92400e',
          900: '#78350f',
        },
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'system-ui', 'sans-serif'],
        sans: ['"Plus Jakarta Sans"', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        'subtle': '0 1px 3px 0 rgba(0, 0, 0, 0.4), 0 1px 2px -1px rgba(0, 0, 0, 0.4)',
        'elevated': '0 10px 25px -5px rgba(0, 0, 0, 0.5), 0 8px 10px -6px rgba(0, 0, 0, 0.5)',
      },
    },
  },
  plugins: [],
}
