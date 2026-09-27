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

export interface AnonymousGroupSummary {
  id: string
  createdAt: string
  memberCount: number
  lastLocationAt: string | null
}

export interface AGMember {
  id: string
  createdAt: string
  isAGAdmin: boolean
  lastLocationAt: string | null
}
