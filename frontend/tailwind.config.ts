// ── SmartHire · frontend/tailwind.config.ts ──
import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          primary: "#6366F1",
          success: "#10B981",
          danger:  "#EF4444",
          bg:      "#0B0F1A",
          card:    "#111827",
          border:  "#1F2937",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
      },
    },
  },
  plugins: [],
} satisfies Config;
