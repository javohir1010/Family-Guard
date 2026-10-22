/** @type {import('tailwindcss').Config} */

// Палитры берутся из CSS-переменных (src/index.css), поэтому одни и те же
// классы (bg-gray-50, text-blue-700 …) автоматически работают в светлой
// и тёмной теме.
const shades = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900];
const palette = (name) =>
  Object.fromEntries(
    shades.map((s) => [s, `rgb(var(--${name}-${s}) / <alpha-value>)`])
  );

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        gray: palette("gray"),
        blue: palette("blue"),
        red: palette("red"),
        green: palette("green"),
        yellow: palette("yellow"),
      },
      fontFamily: {
        sans: [
          "Manrope",
          "Inter",
          "system-ui",
          "-apple-system",
          "BlinkMacSystemFont",
          "sans-serif",
        ],
      },
    },
  },
  plugins: [],
};
