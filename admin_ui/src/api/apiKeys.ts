import { apiFetch } from './client'
import type { ApiKey, ApiKeyCreated } from './types'

export function listApiKeys(): Promise<ApiKey[]> {
  return apiFetch<ApiKey[]>('/api/admin/api-keys')
}

export function createApiKey(): Promise<ApiKeyCreated> {
  return apiFetch<ApiKeyCreated>('/api/admin/api-keys', { method: 'POST' })
}

export function revokeApiKey(id: string): Promise<void> {
  return apiFetch<void>(`/api/admin/api-keys/${id}`, { method: 'DELETE' })
}
