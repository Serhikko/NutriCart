/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// The website is installable ("Add to Home Screen" on iPhone): the PWA plugin
// writes the manifest and a service worker that caches the app shell. Data
// is never cached: every screen asks Supabase and subscribes to changes.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg'],
      manifest: {
        name: 'NutriCart',
        short_name: 'NutriCart',
        description: 'See your day, or your partner’s, and send a nudge.',
        theme_color: '#2e7d4f',
        background_color: '#f6f7f4',
        display: 'standalone',
        start_url: '/',
        icons: [{ src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
    }),
  ],
  build: {
    // Vendor chunks: recharts and supabase-js change rarely, the app often.
    rollupOptions: {
      output: {
        manualChunks: {
          charts: ['recharts'],
          supabase: ['@supabase/supabase-js'],
          react: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
});
