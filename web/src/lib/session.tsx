import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Session } from '@supabase/supabase-js';
import { isConfigured, supabase } from './supabase';

/**
 * The visitor's own Supabase user. Anonymous on first visit, kept by
 * supabase-js in localStorage, refreshed automatically. Since milestone 3 an
 * account may carry an email: linked here or on the phone, it signs in on
 * any device with a magic link, and supabase-js swaps the session in place
 * when that link lands back on the site.
 */
interface SessionState {
  session: Session | null;
  userId: string | null;
  /** The confirmed email of the account, or null while it is anonymous. */
  email: string | null;
  isAnonymous: boolean;
  loading: boolean;
  error: string | null;
}

const EMPTY: SessionState = { session: null, userId: null, email: null, isAnonymous: true, loading: true, error: null };

const SessionContext = createContext<SessionState>(EMPTY);

function fromSession(session: Session | null, loading: boolean, error: string | null): SessionState {
  const anonymous = session?.user.is_anonymous ?? true;
  return {
    session,
    userId: session?.user.id ?? null,
    email: !anonymous && session?.user.email ? session.user.email : null,
    isAnonymous: anonymous,
    loading,
    error,
  };
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>(EMPTY);

  useEffect(() => {
    if (!isConfigured) {
      setState({ ...EMPTY, loading: false, error: 'not_configured' });
      return;
    }
    let cancelled = false;
    (async () => {
      const { data } = await supabase.auth.getSession();
      let session = data.session;
      if (!session) {
        const { data: fresh, error } = await supabase.auth.signInAnonymously();
        if (error) {
          if (!cancelled) setState({ ...EMPTY, loading: false, error: error.message });
          return;
        }
        session = fresh.session;
      }
      if (!cancelled) setState(fromSession(session, false, null));
    })();
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setState((s) => fromSession(session, s.loading, s.error));
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
