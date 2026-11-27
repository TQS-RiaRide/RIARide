import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { createBrowserRouter, createMemoryRouter, RouterProvider } from 'react-router';
import { Toaster } from 'sonner';
import { routes } from './app/routes';
import type { Backend } from './backend/Backend';
import { BackendProvider } from './backend/BackendContext';
import { createLiveBackend } from './backend/liveBackend';
import { createRegistryStore } from './backend/registry';
import { useAuth } from './features/auth/useAuth';

interface AppProps {
  /** Demo mode and tests pass a backend; tests also pass a starting URL, for an in-memory router. */
  backend?: Backend;
  initialPath?: string;
}

function newQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      // Stale-while-revalidate: cached data shows at once and refreshes in the background.
      queries: { staleTime: 10_000, gcTime: 5 * 60_000, retry: 1, refetchOnWindowFocus: true },
      mutations: { retry: 0 },
    },
  });
}

export default function App({ backend: given, initialPath }: AppProps) {
  const { state, getToken } = useAuth();
  const [queryClient] = useState(newQueryClient);
  const userId = state.status === 'authenticated' ? state.user.id : 'anonymous';

  const backend = useMemo(
    () => given ?? createLiveBackend({ getToken, registry: createRegistryStore(() => userId) }),
    [given, getToken, userId],
  );

  const [router] = useState(() =>
    initialPath === undefined
      ? createBrowserRouter(routes)
      : createMemoryRouter(routes, { initialEntries: [initialPath] }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <BackendProvider backend={backend}>
        <RouterProvider router={router} />
        <Toaster
          position="bottom-center"
          offset={{ bottom: 'calc(24px + env(safe-area-inset-bottom))' }}
          mobileOffset={{ bottom: 'calc(88px + env(safe-area-inset-bottom))' }}
          toastOptions={{ className: 'toast sq', duration: 6000 }}
        />
      </BackendProvider>
    </QueryClientProvider>
  );
}
