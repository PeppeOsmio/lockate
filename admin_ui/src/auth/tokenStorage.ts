const STORAGE_KEY = 'lockate_admin_api_key'
const CHANGE_EVENT = 'lockate-admin-token-changed'

export function getToken(): string | null {
  return sessionStorage.getItem(STORAGE_KEY)
}

export function setToken(value: string): void {
  sessionStorage.setItem(STORAGE_KEY, value)
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function clearToken(): void {
  sessionStorage.removeItem(STORAGE_KEY)
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function onTokenChange(listener: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, listener)
  return () => window.removeEventListener(CHANGE_EVENT, listener)
}
