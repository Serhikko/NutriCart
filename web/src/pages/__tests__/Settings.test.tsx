import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { ToastProvider } from '../../components/ui/Toast';

// The two people lists and their removals run on real React Query over this store, so the
// order of events (pending, refetch, settled) is the one the page meets against Supabase.
const store = vi.hoisted(() => ({
  partners: [] as { linkId: string; name: string; since: string }[],
  followed: [] as { linkId: string; ownerId: string; ownerName: string }[],
  /** While set, the lists do not answer (loading). */
  hold: null as Promise<void> | null,
  failRemove: false,
}));

vi.mock('../../lib/session', () => ({ useSession: () => ({ userId: 'u1', email: null, isAnonymous: true, loading: false }) }));
vi.mock('../../lib/supabase', () => ({ supabase: { auth: {} }, isConfigured: true }));
vi.mock('../../lib/tracker', () => ({ useProfileDetails: () => ({ data: null }) }));
vi.mock('../../lib/queries', async () => {
  const { useMutation, useQuery, useQueryClient } = await import('@tanstack/react-query');
  const list =
    <T,>(key: string, read: () => T[]) =>
    () =>
      useQuery({
        queryKey: [key],
        queryFn: async () => {
          if (store.hold) await store.hold;
          return [...read()];
        },
      });
  const remove = (key: string, drop: (id: string) => void) =>
    function useRemove() {
      const qc = useQueryClient();
      return useMutation({
        mutationFn: async (id: string) => {
          if (store.failRemove) throw new Error('offline');
          drop(id);
        },
        onSuccess: () => qc.invalidateQueries({ queryKey: [key] }),
      });
    };
  return {
    useMyPartners: list('partners', () => store.partners),
    useFollowed: list('followed', () => store.followed),
    useRemovePartner: remove('partners', (id) => {
      store.partners = store.partners.filter((p) => p.linkId !== id);
    }),
    useUnfollow: remove('followed', (id) => {
      store.followed = store.followed.filter((a) => a.linkId !== id);
    }),
    useMyProfile: () => ({ data: 'Olena' }),
    useSaveMyName: () => ({ mutate: vi.fn(), isPending: false, isSuccess: false, isError: false }),
    useNewPairingCode: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  };
});

const { Settings } = await import('../Settings');

function renderSettings() {
  localStorage.setItem('nutricart.locale', 'en');
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const router = createMemoryRouter([{ path: '/settings', element: <Settings /> }], { initialEntries: ['/settings'] });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

const section = (name: string) => screen.getByRole('region', { name });

beforeEach(() => {
  store.partners = [
    { linkId: 'p1', name: 'Andrii', since: '2026-01-01' },
    { linkId: 'p2', name: 'Maryna', since: '2026-02-01' },
  ];
  store.followed = [{ linkId: 'f1', ownerId: 'o1', ownerName: 'Andrii' }];
  store.hold = null;
  store.failRemove = false;
});
afterEach(() => localStorage.clear());

describe('Settings', () => {
  it('shows skeleton rows, not a word, while the people lists load', async () => {
    let release = () => {};
    store.hold = new Promise<void>((resolve) => (release = resolve));
    renderSettings();
    for (const name of ['Who can see your day', 'Accounts you follow']) {
      const group = section(name);
      expect(group.querySelector('.set-row .skel')).not.toBeNull();
      // The word is only for screen readers (the skeleton's status), never a visible row.
      expect(within(group).getByRole('status')).toHaveTextContent('Loading…');
      expect(group.querySelector('.set-empty')).toBeNull();
    }
    await act(async () => release());
    expect(await within(section('Who can see your day')).findByText('Maryna')).toBeInTheDocument();
    expect(section('Who can see your day').querySelector('.skel')).toBeNull();
  });

  it('keeps the empty messages for lists that loaded empty', async () => {
    store.partners = [];
    store.followed = [];
    renderSettings();
    expect(await screen.findByText('Nobody yet.')).toBeInTheDocument();
    expect(await screen.findByText('You follow nobody yet.')).toBeInTheDocument();
  });

  it('moves focus to the next row when Remove takes its own row away, then to the section', async () => {
    renderSettings();
    const partners = section('Who can see your day');
    const first = await within(partners).findByRole('button', { name: 'Remove', description: 'Andrii' });
    first.focus();
    fireEvent.click(first);
    await waitFor(() => expect(within(partners).queryByText('Andrii')).toBeNull());
    await waitFor(() => expect(document.activeElement).toBe(within(partners).getByRole('button', { name: 'Remove', description: 'Maryna' })));

    fireEvent.click(document.activeElement as HTMLElement);
    expect(await within(partners).findByText('Nobody yet.')).toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toBe(partners));
  });

  it('moves focus to the section when Stop following empties the list', async () => {
    renderSettings();
    const following = section('Accounts you follow');
    const button = await within(following).findByRole('button', { name: 'Stop following' });
    button.focus();
    fireEvent.click(button);
    expect(await within(following).findByText('You follow nobody yet.')).toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toBe(following));
  });

  it('reads the privacy note with the following list, not with Forget this device', async () => {
    renderSettings();
    const note = /You see only what the account owner shares/;
    expect(within(section('Accounts you follow')).getByText(note)).toBeInTheDocument();
    expect(within(section('Forget this device')).queryByText(note)).toBeNull();
  });

  it('keeps focus on the button when the removal fails', async () => {
    store.failRemove = true;
    renderSettings();
    const partners = section('Who can see your day');
    const first = await within(partners).findByRole('button', { name: 'Remove', description: 'Andrii' });
    first.focus();
    fireEvent.click(first);
    expect(await within(partners).findByRole('alert')).toHaveTextContent('Something went wrong.');
    await waitFor(() => expect(first).not.toBeDisabled());
    expect(document.activeElement).toBe(first);
  });

  it('jumps to a section from the index without scrollIntoView (jsdom has none)', async () => {
    renderSettings();
    const index = screen.getByRole('navigation', { name: 'Settings' });
    expect(() => fireEvent.click(within(index).getByRole('link', { name: 'Your name' }))).not.toThrow();
    expect(document.activeElement).toBe(section('Your name'));
    expect(within(index).getByRole('link', { name: 'Your name' })).toHaveAttribute('aria-current', 'true');
  });
});
