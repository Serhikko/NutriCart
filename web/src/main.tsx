import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { I18nProvider } from './lib/i18n';
import { SessionProvider } from './lib/session';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 15_000, retry: 1 } },
});

// The router lives in App (a data router, for view transitions); the toast
// provider and the shell are in Layout, the route every page renders inside.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <SessionProvider>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </SessionProvider>
    </I18nProvider>
  </StrictMode>,
);
