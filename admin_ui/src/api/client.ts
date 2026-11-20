import { clearToken, getToken } from '../auth/tokenStorage'
import type { AdminGroup, AGMember, ApiKey, ApiKeyCreated, GroupQuery, Page } from './types'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

export class AdminAPIClient {
  private baseUrl: string | null = null

  private async getBaseUrl(): Promise<string> {
    if (this.baseUrl === null) {
      if (import.meta.env.DEV) {
        this.baseUrl = import.meta.env.VITE_API_BASE_URL as string
      } else {
        const config = await fetch('/config.json').then((r) => r.json())
        this.baseUrl = config.apiBaseUrl as string
      }
    }
    return this.baseUrl
  }

  private async apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
    const baseUrl = await this.getBaseUrl()
    const token = getToken()
    let response: Response
    try {
      response = await fetch(`${baseUrl}${path}`, {
        ...init,
        headers: {
          ...(token ? { 'X-API-KEY': token } : {}),
          ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
          ...init?.headers,
        },
      })
    } catch {
      throw new ApiError(0, 'Could not reach the server.')
    }

    if (response.status === 401) {
      clearToken()
      throw new ApiError(401, 'The admin key is no longer accepted.')
    }

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      const code = typeof body?.error === 'string' ? ` (${body.error})` : ''
      throw new ApiError(response.status, `The server answered ${response.status}${code}.`)
    }

    if (response.status === 204) {
      return undefined as T
    }

    return (await response.json()) as T
  }

  async checkApiKey(key: string): Promise<boolean> {
    const baseUrl = await this.getBaseUrl()
    const response = await fetch(`${baseUrl}/api/v1/admin/api-keys`, {
      headers: { 'X-API-KEY': key },
    })
    return response.ok
  }

  listAnonymousGroups(query: GroupQuery): Promise<Page<AdminGroup>> {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
      sort: query.sort,
    })
    if (query.q) params.set('q', query.q)
    if (query.inactiveDays) params.set('inactiveDays', String(query.inactiveDays))
    return this.apiFetch(`/api/v1/admin/anonymous-groups?${params}`)
  }

  getAnonymousGroup(anonymousGroupId: string): Promise<AdminGroup> {
    return this.apiFetch(`/api/v1/admin/anonymous-groups/${anonymousGroupId}`)
  }

  listGroupMembers(anonymousGroupId: string): Promise<AGMember[]> {
    return this.apiFetch(`/api/v1/admin/anonymous-groups/${anonymousGroupId}/members`)
  }

  deleteAnonymousGroup(anonymousGroupId: string): Promise<void> {
    return this.apiFetch(`/api/v1/admin/anonymous-groups/${anonymousGroupId}`, { method: 'DELETE' })
  }

  deleteGroupMember(anonymousGroupId: string, memberId: string): Promise<void> {
    return this.apiFetch(`/api/v1/admin/anonymous-groups/${anonymousGroupId}/members/${memberId}`, {
      method: 'DELETE',
    })
  }

  setGroupMemberAdmin(
    anonymousGroupId: string,
    memberId: string,
    isAGAdmin: boolean,
  ): Promise<void> {
    return this.apiFetch(`/api/v1/admin/anonymous-groups/${anonymousGroupId}/members/${memberId}`, {
      method: 'PATCH',
      body: JSON.stringify({ isAGAdmin }),
    })
  }

  listApiKeys(page = 0, size = 20): Promise<Page<ApiKey>> {
    return this.apiFetch(`/api/v1/admin/api-keys?page=${page}&size=${size}`)
  }

  createApiKey(): Promise<ApiKeyCreated> {
    return this.apiFetch('/api/v1/admin/api-keys', { method: 'POST' })
  }

  revokeApiKey(id: string): Promise<void> {
    return this.apiFetch(`/api/v1/admin/api-keys/${id}`, { method: 'DELETE' })
  }
}
