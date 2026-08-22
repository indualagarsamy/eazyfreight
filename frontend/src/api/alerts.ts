import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  AlertConfigurationView, AlertDashboard, AlertView, NotificationChannel,
} from './types'

const keys = {
  root: ['alerts'] as const,
  open: ['alerts', 'open'] as const,
  dashboard: ['alerts', 'dashboard'] as const,
  detail: (id: string) => ['alerts', 'detail', id] as const,
  forBooking: (id: string) => ['alerts', 'booking', id] as const,
  configurations: ['alerts', 'configurations'] as const,
}

/**
 * The alert list refetches on a timer as well as on mutation. Alerts are the one
 * screen where the interesting change comes from the server rather than from the
 * person looking at it — a deadline passing is not something the browser can know.
 */
export function useAlerts() {
  return useQuery({
    queryKey: keys.open,
    queryFn: () => api.get<AlertView[]>('/api/alerts'),
    refetchInterval: 60_000,
  })
}

export function useAlertDashboard() {
  return useQuery({
    queryKey: keys.dashboard,
    queryFn: () => api.get<AlertDashboard>('/api/alerts/dashboard'),
    refetchInterval: 60_000,
  })
}

export function useAlert(id: string) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => api.get<AlertView>(`/api/alerts/${id}`),
    enabled: id !== '',
  })
}

export function useAlertsForBooking(bookingId: string) {
  return useQuery({
    queryKey: keys.forBooking(bookingId),
    queryFn: () => api.get<AlertView[]>(`/api/alerts/bookings/${bookingId}`),
    enabled: bookingId !== '',
  })
}

export function useAlertConfigurations() {
  return useQuery({
    queryKey: keys.configurations,
    queryFn: () => api.get<AlertConfigurationView[]>('/api/alerts/configurations'),
  })
}

function useAlertMutation<TInput, TResult>(send: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: send,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: keys.root })
    },
  })
}

export const useEvaluateAlerts = () =>
  useAlertMutation<void, { changes: number; open: number }>(() =>
    api.post('/api/alerts/evaluate'))

export const useAcknowledgeAlert = () =>
  useAlertMutation<string, AlertView>((id) => api.post(`/api/alerts/${id}/acknowledge`))

export const useSnoozeAlert = () =>
  useAlertMutation<{ id: string; until: string }, AlertView>(({ id, until }) =>
    api.post(`/api/alerts/${id}/snooze`, { until }))

export const useResolveAlert = () =>
  useAlertMutation<{ id: string; reason: string }, AlertView>(({ id, reason }) =>
    api.post(`/api/alerts/${id}/resolve`, { reason }))

export const useUpdateAlertConfiguration = () =>
  useAlertMutation<{
    alertType: string
    enabled: boolean
    thresholdDays: number | null
    escalationHours: number
    channels: NotificationChannel[]
    snoozeMaxHours: number
    customMessage: string | null
  }, AlertConfigurationView>(({ alertType, ...body }) =>
    api.put(`/api/alerts/configurations/${alertType}`, body))
