import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Welcome } from './pages/Welcome';
import { Day } from './pages/Day';
import { Week } from './pages/Week';
import { Settings } from './pages/Settings';

/**
 * Routes: the welcome page (code entry) at the root, and one set of tabs per
 * followed account under /a/<owner id>. A partner who follows two people
 * switches between them from the welcome page or Settings.
 */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Welcome />} />
        <Route path="/a/:ownerId/day" element={<Day />} />
        <Route path="/a/:ownerId/week" element={<Week />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
