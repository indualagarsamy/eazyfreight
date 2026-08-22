import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { AppShell } from './components/AppShell'
import { ToastProvider } from './components/Toast'
import { QuotesPage } from './pages/QuotesPage'
import { NewQuotePage } from './pages/NewQuotePage'
import { QuoteDetailPage } from './pages/QuoteDetailPage'
import { BookingsPage } from './pages/BookingsPage'
import { NewBookingPage } from './pages/NewBookingPage'
import { BookingDetailPage } from './pages/BookingDetailPage'
import { LogisticsPage } from './pages/LogisticsPage'
import { LogisticsDetailPage } from './pages/LogisticsDetailPage'
import { DocumentationPage } from './pages/DocumentationPage'
import { DocumentationDetailPage } from './pages/DocumentationDetailPage'
import { FilingsPage } from './pages/FilingsPage'
import { FilingDetailPage } from './pages/FilingDetailPage'
import { FinancePage } from './pages/FinancePage'
import { FinanceDetailPage } from './pages/FinanceDetailPage'
import './styles/global.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 5_000,
      // A 4xx is an answer, not a blip — only retry once, and never for those.
      retry: (failureCount, error) => {
        const status = (error as { status?: number }).status ?? 0
        if (status >= 400 && status < 500) return false
        return failureCount < 1
      },
    },
  },
})

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/quotes" replace /> },
      { path: 'quotes', element: <QuotesPage /> },
      { path: 'quotes/new', element: <NewQuotePage /> },
      { path: 'quotes/:id', element: <QuoteDetailPage /> },
      { path: 'bookings', element: <BookingsPage /> },
      { path: 'bookings/new', element: <NewBookingPage /> },
      { path: 'bookings/:id', element: <BookingDetailPage /> },
      { path: 'logistics', element: <LogisticsPage /> },
      { path: 'logistics/:bookingId', element: <LogisticsDetailPage /> },
      { path: 'documentation', element: <DocumentationPage /> },
      { path: 'documentation/:id', element: <DocumentationDetailPage /> },
      { path: 'compliance', element: <FilingsPage /> },
      { path: 'compliance/:id', element: <FilingDetailPage /> },
      { path: 'finance', element: <FinancePage /> },
      { path: 'finance/:id', element: <FinanceDetailPage /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)
