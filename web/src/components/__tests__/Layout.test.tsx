import { describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { I18nProvider } from '../../lib/i18n';
import { todayEpochDay } from '../../lib/dates';
import { guessMealSlot } from '../../domain/habits';
import { LargeTitle } from '../ui/LargeTitle';
import { useToast } from '../ui/Toast';

const profile = vi.hoisted(() => ({ onboarded: true }));
vi.mock('../../lib/session', () => ({ useSession: () => ({ userId: 'u1', email: null, session: null }) }));
vi.mock('../../lib/tracker', () => ({ useProfileDetails: () => ({ data: profile.onboarded ? { sex: 'FEMALE' } : null }) }));
vi.mock('../../lib/queries', () => ({
  useFollowed: () => ({ data: [] }),
  useMyProfile: () => ({ data: 'Olena' }),
  useDay: () => ({ data: undefined }),
}));

const { Layout } = await import('../Layout');

// ScrollRestoration scrolls on every navigation; jsdom has no scrolling.
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;

function ToastButton() {
  const toast = useToast();
  return (
    <button type="button" onClick={() => toast({ text: 'Added to Dinner · 161 kcal', undo: () => undefined })}>
      toast
    </button>
  );
}

function renderAt(path: string) {
  localStorage.setItem('nutricart.locale', 'en');
  const page = (
    <>
      <LargeTitle title="My day" compact="My day · 870 left" />
      <ToastButton />
    </>
  );
  const router = createMemoryRouter(
    [
      {
        element: <Layout />,
        children: [
          { path: '/', element: <p>home</p> },
          { path: '/me/day', element: page },
          { path: '/me/week', element: <p>week</p> },
          {
            path: '/a/:ownerId/day',
            element: (
              <section id="nudge" aria-label="Send a nudge">
                <textarea aria-label="Message" />
              </section>
            ),
          },
          { path: '/settings', element: <p>settings</p> },
        ],
      },
    ],
    { initialEntries: [path] },
  );
  return render(
    <I18nProvider>
      <RouterProvider router={router} />
    </I18nProvider>,
  );
}

describe('Layout (no matchMedia: the phone shell)', () => {
  it('shows the four tabs by their exact names and marks the current one', () => {
    profile.onboarded = true;
    renderAt('/me/day');
    const nav = screen.getByRole('navigation', { name: 'Sections' });
    expect(within(nav).getByRole('link', { name: 'Week' })).toHaveAttribute('href', '/me/week');
    expect(within(nav).getByRole('link', { name: 'Day' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/');
    expect(within(nav).getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/settings');
  });

  it('adds food to the meal guessed from the hour', () => {
    profile.onboarded = true;
    renderAt('/me/day');
    const add = screen.getByRole('link', { name: 'Add food' });
    expect(add).toHaveAttribute('href', `/me/add/${todayEpochDay()}/${guessMealSlot(new Date().getHours())}`);
  });

  it('has no Day, Week or + before onboarding', () => {
    profile.onboarded = false;
    renderAt('/');
    const nav = screen.getByRole('navigation', { name: 'Sections' });
    expect(within(nav).queryByRole('link', { name: 'Week' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Add food' })).toBeNull();
  });

  it('turns + into the nudge bell on a partner’s pages', () => {
    profile.onboarded = true;
    renderAt('/a/p1/day');
    expect(screen.getByRole('button', { name: 'Send a nudge' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Week' })).toHaveAttribute('href', '/a/p1/week');
  });

  it('brings the nudge card up from the bell, even where elements cannot scroll', () => {
    profile.onboarded = true;
    renderAt('/a/p1/day');
    // jsdom has no scrollIntoView: the bell must still work, and the message field takes the focus.
    expect(typeof Element.prototype.scrollIntoView).toBe('undefined');
    act(() => fireEvent.click(screen.getByRole('button', { name: 'Send a nudge' })));
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveFocus();
  });

  it('carries the page title into the compact top bar, and shows toasts with Undo', () => {
    profile.onboarded = true;
    const { container } = renderAt('/me/day');
    expect(screen.getByRole('heading', { level: 1, name: 'My day' })).toBeInTheDocument();
    expect(container.querySelector('.topbar')?.textContent).toBe('My day · 870 left');
    act(() => fireEvent.click(screen.getByRole('button', { name: 'toast' })));
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('Added to Dinner · 161 kcal');
    expect(within(status).getByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });
});

describe('Layout during a view transition', () => {
  it('starts a first open’s entrances at once, under the old page’s fade, so the screen is never empty', async () => {
    sessionStorage.clear();
    profile.onboarded = true;
    let finish: () => void = () => undefined;
    const finished = new Promise<void>((resolve) => {
      finish = resolve;
    });
    // jsdom has no View Transitions: a stand-in that runs the update and finishes when told.
    const doc = document as unknown as Record<string, unknown>;
    doc.startViewTransition = (update: () => unknown) => {
      const updateCallbackDone = Promise.resolve().then(update);
      return { finished, ready: updateCallbackDone, updateCallbackDone, skipTransition: () => finish() };
    };
    try {
      const { container } = renderAt('/me/day');
      const nav = screen.getByRole('navigation', { name: 'Sections' });
      await act(async () => {
        fireEvent.click(within(nav).getByRole('link', { name: 'Week' }));
      });
      await screen.findByText('week');
      const app = container.querySelector('.app');
      expect(app).toHaveClass('first');
      expect(app).not.toHaveClass('held');
      expect(document.documentElement).toHaveAttribute('data-vt-first');
      await act(async () => {
        finish();
        await finished;
      });
      expect(container.querySelector('.app')).toHaveClass('first');
      expect(document.documentElement).not.toHaveAttribute('data-vt-first');
    } finally {
      delete doc.startViewTransition;
    }
  });
});
