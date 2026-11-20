import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { AdminAPIClientProvider } from '../api/AdminAPIClientProvider'
import type { AdminAPIClient } from '../api/client'
import { NotifyProvider } from '../feedback/NotifyProvider'
import { ColorModeProvider } from '../theme/ColorModeProvider'

export function renderPage(
  page: ReactNode,
  { client, path, route }: { client: Partial<AdminAPIClient>; path: string; route: string },
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <AdminAPIClientProvider client={client as AdminAPIClient}>
      <QueryClientProvider client={queryClient}>
        <ColorModeProvider>
          <NotifyProvider>
            <MemoryRouter initialEntries={[route]}>
              <Routes>
                <Route path={path} element={page} />
                <Route path="*" element={<div>elsewhere</div>} />
              </Routes>
            </MemoryRouter>
          </NotifyProvider>
        </ColorModeProvider>
      </QueryClientProvider>
    </AdminAPIClientProvider>,
  )
}
