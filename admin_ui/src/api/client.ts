import { clearToken, getToken } from '../auth/tokenStorage'
import type { AGMember, AnonymousGroupSummary, ApiKey, ApiKeyCreated } from './types'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export class AdminAPIClient {
  private baseUrl: string | null = null

  private async getBaseUrl(): Promise<string> {
    if (this.baseUrl === null) {
      if (import.meta.env.DEV) {
        this.baseUrl = import.meta.env.VITE_API_BASE_URL as string
      } else {
        const config = await fetch('/config.json').then(r => r.json())
        this.baseUrl = config.apiBaseUrl as string
      }
    }
    return this.baseUrl
  }

  private async apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
    const baseUrl = await this.getBaseUrl()
    const token = getToken()
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        ...(token ? { 'X-API-KEY': token } : {}),
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    })

    if (response.status === 401) {
      clearToken()
      throw new ApiError(401, 'Unauthorized')
    }

    if (!response.ok) {
      throw new ApiError(response.status, `Request to ${path} failed with status ${response.status}`)
    }

    if (response.status === 204) {
      return undefined as T
    }

    return (await response.json()) as T
  }

  async checkApiKey(key: string): Promise<boolean> {
    const baseUrl = await this.getBaseUrl()
    const response = await fetch(`${baseUrl}/api/admin/api-keys`, {
      headers: { 'X-API-KEY': key },
    })
    return response.ok
  }

  listAnonymousGroups(): Promise<AnonymousGroupSummary[]> {
    return this.apiFetch('/api/admin/anonymous-groups')
  }

  listGroupMembers(anonymousGroupId: string): Promise<AGMember[]> {
    return this.apiFetch(`/api/admin/anonymous-groups/${anonymousGroupId}/members`)
  }

  listApiKeys(): Promise<ApiKey[]> {
    return this.apiFetch('/api/admin/api-keys')
  }

  createApiKey(): Promise<ApiKeyCreated> {
    return this.apiFetch('/api/admin/api-keys', { method: 'POST' })
  }

  revokeApiKey(id: string): Promise<void> {
    return this.apiFetch(`/api/admin/api-keys/${id}`, { method: 'DELETE' })
  }
}

