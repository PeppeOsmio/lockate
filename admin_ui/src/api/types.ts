export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiKey {
  id: string
  createdAt: string
  lastValidated: string | null
}

export interface ApiKeyCreated extends ApiKey {
  key: string
}

export interface AdminGroup {
  id: string
  createdAt: string
  memberCount: number
  adminCount: number
  onlineCount: number
  pushRegisteredCount: number
  circleCount: number
  lastLocationAt: string | null
}

export type GroupSort = 'created' | 'members' | 'activity'

export interface GroupQuery {
  page: number
  size: number
  q?: string
  sort: GroupSort
  inactiveDays?: number
}

export interface AGMember {
  id: string
  createdAt: string
  isAGAdmin: boolean
  online: boolean
  pushRegistered: boolean
  lastLocationAt: string | null
}
