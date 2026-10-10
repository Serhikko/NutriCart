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
  /** The account's saved name ('' for none) and its questionnaire (primary_client), if any. */
  profile: 'Olena',
  details: null as { primary_client: 'phone' | 'web'; sex: 'FEMALE'; birth_date: string; height_cm: number; goal: 'MAINTAIN'; custom_kcal_target: null } | null,
  newCode: vi.fn(),
}));

vi.mock('../../lib/session', () => ({ useSession: () => ({ userId: 'u1', email: null, isAnonymous: true, loading: false }) }));
vi.mock('../../lib/supabase', () => ({ supabase: { auth: {} }, isConfigured: true }));
vi.mock('../../lib/tracker', () => ({ useProfileDetails: () => ({ data: store.details }) }));
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
    useMyProfile: () => ({ data: store.profile, isSuccess: true }),
    useSaveMyName: () => ({ mutate: vi.fn(), isPending: false, isSuccess: false, isError: false }),
    useNewPairingCode: () => ({ mutate: store.newCode, isPending: false, isError: false }),
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
  store.profile = 'Olena';
  store.details = null;
  store.newCode = vi.fn();
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

  it('lists the four languages by their own names, and switches the page to the one picked', async () => {
    renderSettings();
    const group = within(section('Language')).getByRole('radiogroup', { name: 'Language' });
    const radios = within(group).getAllByRole('radio');
    expect(radios.map((r) => r.getAttribute('aria-label'))).toEqual(['English', 'Українська', 'Беларуская', 'Русский']);
    expect(radios.map((r) => r.closest('[lang]')?.getAttribute('lang'))).toEqual(['en', 'uk', 'be', 'ru']);
    expect(within(group).getByRole('radio', { name: 'English' })).toBeChecked();

    fireEvent.click(within(group).getByRole('radio', { name: 'Беларуская' }));
    await waitFor(() => expect(document.documentElement.lang).toBe('be'));
    expect(localStorage.getItem('nutricart.locale')).toBe('be');
    expect(within(group).getByRole('radio', { name: 'Беларуская' })).toBeChecked();
    expect(within(group).getByRole('radio', { name: 'English' })).not.toBeChecked();

    fireEvent.click(within(group).getByRole('radio', { name: 'Русский' }));
    await waitFor(() => expect(document.documentElement.lang).toBe('ru'));
    expect(localStorage.getItem('nutricart.locale')).toBe('ru');
  });

  it('shows the stored pairing code only to the account that made it', async () => {
    const later = Date.now() + 10 * 60_000;
    // Made by another account in this browser (before a sign-in, or before Forget this device).
    localStorage.setItem('nutricart.pairing', JSON.stringify({ owner: 'someone-else', code: 'K7QM2P', expiresAt: later }));
    const { unmount } = renderSettings();
    expect(within(section('Share your day')).queryByText('K7QM2P')).toBeNull();
    unmount();

    // Stored before codes carried their account: not shown either.
    localStorage.setItem('nutricart.pairing', JSON.stringify({ code: 'K7QM2P', expiresAt: later }));
    const second = renderSettings();
    expect(within(section('Share your day')).queryByText('K7QM2P')).toBeNull();
    second.unmount();

    localStorage.setItem('nutricart.pairing', JSON.stringify({ owner: 'u1', code: 'K7QM2P', expiresAt: later }));
    renderSettings();
    expect(within(section('Share your day')).getByText('K7QM2P')).toBeInTheDocument();
  });

  it('asks for a name before making a code, so the partner does not follow "NutriCart"', () => {
    store.profile = '';
    renderSettings();
    const share = section('Share your day');
    expect(within(share).getByText('Save your name below first, so they know whose day it is.')).toBeInTheDocument();
    const button = within(share).getByRole('button', { name: 'New code' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(store.newCode).not.toHaveBeenCalled();
  });

  it("shows a phone account's name without a field, since the phone would put its own back", () => {
    store.details = { primary_client: 'phone', sex: 'FEMALE', birth_date: '1990-01-01', height_cm: 170, goal: 'MAINTAIN', custom_kcal_target: null };
    renderSettings();
    const name = section('Your name');
    expect(within(name).getByText('Olena')).toBeInTheDocument();
    expect(within(name).getByText(/Your name is set in the NutriCart app on your phone/)).toBeInTheDocument();
    expect(within(name).queryByRole('textbox')).toBeNull();
    expect(within(name).queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('warns an anonymous follower that signing in to another account leaves the follows behind', async () => {
    renderSettings();
    expect(await within(section('Account')).findByText(/You follow people from this browser's account/)).toBeInTheDocument();
  });
});
