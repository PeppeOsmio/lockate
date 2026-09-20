import { apiFetch } from './client'
import type { AGMember, AnonymousGroupSummary } from './types'

export function listAnonymousGroups(): Promise<AnonymousGroupSummary[]> {
  return apiFetch<AnonymousGroupSummary[]>('/api/admin/anonymous-groups')
}

export function listGroupMembers(anonymousGroupId: string): Promise<AGMember[]> {
  return apiFetch<AGMember[]>(`/api/admin/anonymous-groups/${anonymousGroupId}/members`)
}
