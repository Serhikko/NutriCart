import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';

// The real Welcome, queries.ts hooks and React Query, over a stand-in for supabase-js that answers
// as PostgREST does: a request that never left the browser comes back with status 0 and the
// browser's own message, without throwing.
type Answer = { data: unknown; error: { code: string; message: string } | null; status: number };
const net = vi.hoisted(() => ({
  calls: [] as string[],
  /** The partner's saved name ('' for none). */
  profile: '',
  primaryClient: null as 'phone' | 'web' | null,
  /** The profiles upsert's answer; null saves the name. */
  saveAnswer: null as Answer | null,
  saveDelay: 0,
  /** The RPC's answer. */
  rpc: { data: [{ owner_id: 'o1', display_name: 'Test Owner' }], error: null, status: 200 } as Answer,
  rpcDelay: 0,
}));
const session = vi.hoisted(() => ({ userId: 'u1' as string | null, loading: false, error: null as string | null }));

vi.mock('../../lib/session', () => ({ useSession: () => session }));
vi.mock('../../lib/supabase', () => {
  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
  const from = (table: string) => {
    let upserted: { display_name: string } | null = null;
    const answer = async (): Promise<Answer> => {
      if (table === 'profiles' && upserted) {
        net.calls.push(`upsert:${upserted.display_name}`);
        await wait(net.saveDelay);
        if (net.saveAnswer) return net.saveAnswer;
        net.profile = upserted.display_name;
        return { data: null, error: null, status: 201 };
      }
      if (table === 'profiles') return { data: net.profile ? { display_name: net.profile } : null, error: null, status: 200 };
      if (table === 'profile_details') return { data: net.primaryClient ? { primary_client: net.primaryClient } : null, error: null, status: 200 };
      return { data: [], error: null, status: 200 };
    };
    const query: Record<string, unknown> = {};
    Object.assign(query, {
      select: () => query,
      eq: () => query,
      is: () => query,
      order: () => query,
      maybeSingle: answer,
      upsert: (row: { display_name: string }) => {
        upserted = row;
        return query;
      },
      then: (resolve: (value: Answer) => unknown, reject: (reason: unknown) => unknown) => answer().then(resolve, reject),
    });
    return query;
  };
  const rpc = async (_name: string, args: { p_code: string }) => {
    net.calls.push(`rpc:${args.p_code}`);
    await wait(net.rpcDelay);
    return net.rpc;
  };
  return { supabase: { from, rpc }, isConfigured: true };
});

const { Welcome } = await import('../Welcome');

function renderWelcome() {
  localStorage.setItem('nutricart.locale', 'en');
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/', element: <Welcome /> },
      { path: '/a/:ownerId/day', element: <p>their day</p> },
    ],
    { initialEntries: ['/'] },
  );
  render(
    <QueryClientProvider client={client}>
      <I18nProvider>
        <RouterProvider router={router} />
      </I18nProvider>
    </QueryClientProvider>,
  );
  return router;
}

const form = () => screen.getByRole('form', { name: "Follow someone's day" });
const connect = () => within(form()).getByRole('button', { name: /Connect/ });
const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

beforeEach(() => {
  net.calls = [];
  net.profile = '';
  net.primaryClient = null;
  net.saveAnswer = null;
  net.saveDelay = 0;
  net.rpc = { data: [{ owner_id: 'o1', display_name: 'Test Owner' }], error: null, status: 200 };
  net.rpcDelay = 0;
  session.userId = 'u1';
  session.loading = false;
  localStorage.removeItem('nutricart.pairing');
  session.error = null;
});

describe('Welcome: following someone', () => {
  it('saves the name, then redeems the code, then opens their day', async () => {
    const router = renderWelcome();
    type('Pairing code', 'h46mcw');
    type('Your name (they will see it)', 'Sam');
    fireEvent.click(connect());
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toEqual(['upsert:Sam', 'rpc:H46MCW']);
  });

  it('reads a code typed on a Cyrillic keyboard as the Latin one it looks like', async () => {
    const router = renderWelcome();
    type('Pairing code', 'н46мсw');
    type('Your name (they will see it)', 'Sam');
    expect(connect()).not.toBeDisabled();
    fireEvent.click(connect());
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toContain('rpc:H46MCW');
  });

  it('asks a first-time partner for a name instead of sending them as "Partner"', async () => {
    renderWelcome();
    type('Pairing code', 'H46MCW');
    await waitFor(() => expect(screen.getByLabelText('Your name (they will see it)')).toHaveAttribute('aria-required', 'true'));
    fireEvent.click(connect());
    expect(await screen.findByText('Add your name, so they know who follows their day.')).toBeInTheDocument();
    expect(screen.getByLabelText('Your name (they will see it)')).toHaveFocus();
    expect(net.calls).toEqual([]);
  });

  it('shows a failed name save and keeps the code for another try, instead of doing nothing', async () => {
    net.saveAnswer = { data: null, error: { code: '', message: 'TypeError: Load failed' }, status: 0 };
    const router = renderWelcome();
    type('Pairing code', 'H46MCW');
    type('Your name (they will see it)', 'Sam');
    fireEvent.click(connect());
    expect(await screen.findByText('You seem to be offline.')).toBeInTheDocument();
    expect(net.calls).toEqual(['upsert:Sam']);
    expect(connect()).toHaveTextContent('Connect');
    expect(connect()).not.toBeDisabled();

    net.saveAnswer = null;
    fireEvent.click(connect());
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toEqual(['upsert:Sam', 'upsert:Sam', 'rpc:H46MCW']);
  });

  it('sends the name and the code once when Connect is pressed twice while the name saves', async () => {
    net.saveDelay = 50;
    net.rpcDelay = 50;
    const router = renderWelcome();
    type('Pairing code', 'H46MCW');
    type('Your name (they will see it)', 'Sam');
    fireEvent.click(connect());
    fireEvent.click(connect());
    await waitFor(() => expect(connect()).toHaveTextContent('Connecting…'));
    expect(connect()).toBeDisabled();
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toEqual(['upsert:Sam', 'rpc:H46MCW']);
  });

  it("says offline when Safari's fetch fails, not \"Something went wrong\"", async () => {
    net.profile = 'Sam';
    net.rpc = { data: null, error: { code: '', message: 'TypeError: Load failed' }, status: 0 };
    renderWelcome();
    type('Pairing code', 'H46MCW');
    await act(async () => fireEvent.click(connect()));
    expect(await screen.findByText('You seem to be offline.')).toBeInTheDocument();
    expect(screen.queryByText('TypeError: Load failed')).not.toBeInTheDocument();
  });

  it('says the session is gone when the database answers "not signed in"', async () => {
    net.profile = 'Sam';
    net.rpc = { data: null, error: { code: '28000', message: 'not signed in' }, status: 401 };
    renderWelcome();
    type('Pairing code', 'H46MCW');
    await act(async () => fireEvent.click(connect()));
    expect(await screen.findByText('This browser is not signed in to NutriCart right now. Reload the page and try again.')).toBeInTheDocument();
  });

  it('keeps the database words under "Something went wrong" for anything else', async () => {
    net.profile = 'Sam';
    net.rpc = { data: null, error: { code: '42702', message: 'column reference "owner_id" is ambiguous' }, status: 400 };
    renderWelcome();
    type('Pairing code', 'H46MCW');
    await act(async () => fireEvent.click(connect()));
    expect(await screen.findByText('Something went wrong. Try again.')).toBeInTheDocument();
    expect(screen.getByText('42702 column reference "owner_id" is ambiguous')).toBeInTheDocument();
  });

  it('says so, and waits, when the browser has no session', async () => {
    session.userId = null;
    session.error = 'Request rate limit reached';
    renderWelcome();
    type('Pairing code', 'H46MCW');
    expect(screen.getByText('This browser is not signed in to NutriCart right now. Reload the page and try again.')).toBeInTheDocument();
    expect(connect()).toBeDisabled();
  });

  it('says "your own code" for the code this browser shows for the account, without asking the database', async () => {
    net.profile = 'Sam';
    localStorage.setItem('nutricart.pairing', JSON.stringify({ owner: 'u1', code: 'H46MCW', expiresAt: Date.now() + 600_000 }));
    renderWelcome();
    type('Pairing code', 'h46mcw');
    await act(async () => fireEvent.click(connect()));
    expect(await screen.findByText('That is your own code.')).toBeInTheDocument();
    expect(net.calls).not.toContain('rpc:H46MCW');
  });

  it("redeems a code this browser shows for another account (it is that account's, not yours)", async () => {
    net.profile = 'Sam';
    localStorage.setItem('nutricart.pairing', JSON.stringify({ owner: 'u2', code: 'H46MCW', expiresAt: Date.now() + 600_000 }));
    const router = renderWelcome();
    type('Pairing code', 'H46MCW');
    fireEvent.click(connect());
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toContain('rpc:H46MCW');
  });

  it("does not offer a phone account's name, which the app owns", async () => {
    net.profile = 'Phone Name';
    net.primaryClient = 'phone';
    const router = renderWelcome();
    await waitFor(() => expect(screen.queryByLabelText('Your name (they will see it)')).not.toBeInTheDocument());
    type('Pairing code', 'H46MCW');
    fireEvent.click(connect());
    await waitFor(() => expect(router.state.location.pathname).toBe('/a/o1/day'));
    expect(net.calls).toEqual(['rpc:H46MCW']);
  });
});
