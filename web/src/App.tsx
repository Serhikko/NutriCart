import { useState } from 'react';
import { Navigate, RouterProvider, createBrowserRouter, useRouteError, type RouteObject } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Welcome } from './pages/Welcome';
import { Day } from './pages/Day';
import { Week } from './pages/Week';
import { Settings } from './pages/Settings';
import { Onboarding } from './pages/Onboarding';
import { MyDay } from './pages/MyDay';
import { MyWeek } from './pages/MyWeek';
import { AddFood } from './pages/AddFood';
import { useI18n } from './lib/i18n';
import { Button } from './components/ui/Button';
import { Icon } from './components/ui/Icon';

/** A page that crashed, shown inside the shell so the tabs still work. */
function PageError() {
  const { t } = useI18n();
  const error = useRouteError();
  console.error(error);
  return (
    <section className="card page-error" role="alert">
      <Icon name="warning" className="danger-text" />
      <p className="headline">{t('error.generic')}</p>
      <Button variant="fill" type="button" onClick={() => window.location.reload()}>
        {t('error.retry')}
      </Button>
    </section>
  );
}

/**
 * Routes: the welcome page (code entry, own-tracker entry) at the root, the
 * user's own tracker under /me, and one set of tabs per followed account
 * under /a/<owner id>. A data router, so links and navigate() can run as
 * view transitions (`viewTransition`), and scroll restores on back.
 */
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        errorElement: <PageError />,
        children: [
          { path: '/', element: <Welcome /> },
          { path: '/a/:ownerId/day', element: <Day /> },
          { path: '/a/:ownerId/week', element: <Week /> },
          { path: '/me/onboarding', element: <Onboarding /> },
          { path: '/me/day', element: <MyDay /> },
          { path: '/me/week', element: <MyWeek /> },
          { path: '/me/add/:epochDay/:slot', element: <AddFood /> },
          { path: '/settings', element: <Settings /> },
          { path: '*', element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
];

export function App() {
  const [router] = useState(() => createBrowserRouter(routes));
  return <RouterProvider router={router} />;
}
