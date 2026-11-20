import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAdminApiClient } from './adminApiClient'
import { isNotFound } from './client'
import type { GroupQuery } from './types'

// Deleting what is already gone is what was asked
async function ignoreNotFound(request: Promise<void>): Promise<void> {
  try {
    await request
  } catch (e) {
    if (!isNotFound(e)) throw e
  }
}

export function useOverview() {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['overview'],
    queryFn: () => client.getOverview(),
    refetchInterval: 60_000,
  })
}

export function useGroups(query: GroupQuery) {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['groups', query],
    queryFn: () => client.listAnonymousGroups(query),
    placeholderData: keepPreviousData,
  })
}

export function useGroup(groupId: string) {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['group', groupId],
    queryFn: () => client.getAnonymousGroup(groupId),
  })
}

export function useGroupMembers(groupId: string) {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['group-members', groupId],
    queryFn: () => client.listGroupMembers(groupId),
  })
}

export function useApiKeys(page: number, size: number) {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['api-keys', page, size],
    queryFn: () => client.listApiKeys(page, size),
    placeholderData: keepPreviousData,
  })
}

export function useDeleteGroup() {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (groupId: string) => ignoreNotFound(client.deleteAnonymousGroup(groupId)),
    onSuccess: (_data, groupId) => {
      // Not refetched now: the group's page may still be open, and would flash "doesn't exist"
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId], refetchType: 'none' })
      queryClient.invalidateQueries({ queryKey: ['group', groupId], refetchType: 'none' })
      return queryClient.invalidateQueries({ queryKey: ['groups'] })
    },
  })
}

// One at a time, so a failure stops nothing and the server isn't flooded; says how many failed
export function useDeleteGroups() {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (groupIds: string[]) => {
      let failed = 0
      for (const groupId of groupIds) {
        try {
          await ignoreNotFound(client.deleteAnonymousGroup(groupId))
        } catch {
          failed++
        }
      }
      return { deleted: groupIds.length - failed, failed }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
  })
}

export function useRemoveMember(groupId: string) {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (memberId: string) => ignoreNotFound(client.deleteGroupMember(groupId, memberId)),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['group-members', groupId] }),
        queryClient.invalidateQueries({ queryKey: ['group', groupId] }),
        queryClient.invalidateQueries({ queryKey: ['groups'] }),
      ]),
  })
}

export function useSetMemberAdmin(groupId: string) {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ memberId, isAGAdmin }: { memberId: string; isAGAdmin: boolean }) =>
      client.setGroupMemberAdmin(groupId, memberId, isAGAdmin),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['group-members', groupId] }),
        queryClient.invalidateQueries({ queryKey: ['group', groupId] }),
      ]),
  })
}

export function useCreateApiKey() {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => client.createApiKey(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}

export function useRevokeApiKey() {
  const client = useAdminApiClient()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => ignoreNotFound(client.revokeApiKey(id)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  })
}
