import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Session } from '@supabase/supabase-js';
import { isConfigured, supabase } from './supabase';

/**
 * The partner's own anonymous user. Created on first visit, kept by
 * supabase-js in localStorage, refreshed automatically. There is no login
 * form anywhere: the pairing code is the only handshake.
 */
interface SessionState {
  session: Session | null;
  userId: string | null;
  loading: boolean;
  error: string | null;
}

const SessionContext = createContext<SessionState>({ session: null, userId: null, loading: true, error: null });

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ session: null, userId: null, loading: true, error: null });

  useEffect(() => {
    if (!isConfigured) {
      setState({ session: null, userId: null, loading: false, error: 'not_configured' });
      return;
    }
    let cancelled = false;
    (async () => {
      const { data } = await supabase.auth.getSession();
      let session = data.session;
      if (!session) {
        const { data: fresh, error } = await supabase.auth.signInAnonymously();
        if (error) {
          if (!cancelled) setState({ session: null, userId: null, loading: false, error: error.message });
          return;
        }
        session = fresh.session;
      }
      if (!cancelled) setState({ session, userId: session?.user.id ?? null, loading: false, error: null });
    })();
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setState((s) => ({ ...s, session, userId: session?.user.id ?? null }));
    });
    return () => {
      cancelled = true;
      sub.subscription.unsubscribe();
    };
  }, []);

  return <SessionContext.Provider value={state}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionState {
  return useContext(SessionContext);
}
