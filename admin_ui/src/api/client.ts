import { clearToken, getToken } from '../auth/tokenStorage'

export const BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken()
  const response = await fetch(`${BASE_URL}${path}`, {
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
