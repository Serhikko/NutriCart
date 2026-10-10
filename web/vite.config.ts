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
      includeAssets: ['icon.svg', 'icon-maskable.svg', 'icon-180.png', 'icon-512.png', 'icon-maskable-512.png'],
      manifest: {
        name: 'NutriCart',
        short_name: 'NutriCart',
        description: 'See your day, or your partner’s, and send a nudge.',
        // The Ember stage colour, so the splash screen and title bar match the first frame.
        theme_color: '#F5F5F7',
        background_color: '#F5F5F7',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
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
