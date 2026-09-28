import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Welcome } from './pages/Welcome';
import { Day } from './pages/Day';
import { Week } from './pages/Week';
import { Settings } from './pages/Settings';
import { Onboarding } from './pages/Onboarding';
import { MyDay } from './pages/MyDay';
import { MyWeek } from './pages/MyWeek';
import { AddFood } from './pages/AddFood';

/**
 * Routes: the welcome page (code entry, own-tracker entry) at the root, the
 * user's own tracker under /me, and one set of tabs per followed account
 * under /a/<owner id>.
 */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Welcome />} />
        <Route path="/a/:ownerId/day" element={<Day />} />
        <Route path="/a/:ownerId/week" element={<Week />} />
        <Route path="/me/onboarding" element={<Onboarding />} />
        <Route path="/me/day" element={<MyDay />} />
        <Route path="/me/week" element={<MyWeek />} />
        <Route path="/me/add/:epochDay/:slot" element={<AddFood />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
