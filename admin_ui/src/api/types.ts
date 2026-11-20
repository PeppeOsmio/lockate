export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiKey {
  id: string
  label: string | null
  createdAt: string
  expiresAt: string | null
  // At most 10 minutes older than the last use
  lastValidated: string | null
}

export interface ApiKeyCreated extends Omit<ApiKey, 'lastValidated'> {
  key: string
}

// What the admin sets on a key; expiresAt is a server time (UTC, no offset)
export interface ApiKeyWrite {
  label: string | null
  expiresAt: string | null
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
  noAdmin?: boolean
}

export interface JobStatus {
  lastRunAt: string | null
  lastCount: number | null
  overdue: boolean
}

export interface OverviewCounts {
  groups: number
  members: number
  pushRegisteredMembers: number
  circles: number
  groupsWithoutAdmin: number
  inactiveGroups: number
  savedLocations: number
  apiKeys: number
}

// What the database knows is null when it is down, what Redis knows when Redis is
export interface Overview {
  version: string
  apis: number[]
  startedAt: string
  requireApiKey: boolean
  pushRelayUrl: string | null
  locationRetentionSeconds: number
  locationSaveIntervalSeconds: number
  locationRetentionCron: string
  databaseUp: boolean
  redisUp: boolean
  counts: OverviewCounts | null
  onlineMembers: number | null
  locationSave: JobStatus | null
  locationRetention: JobStatus | null
}

export interface AGMember {
  id: string
  createdAt: string
  isAGAdmin: boolean
  online: boolean
  pushRegistered: boolean
  lastLocationAt: string | null
}
