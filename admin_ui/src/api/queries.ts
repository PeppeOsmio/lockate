import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAdminApiClient } from './adminApiClient'
import { isNotFound } from './client'

// Deleting what is already gone is what was asked
async function ignoreNotFound(request: Promise<void>): Promise<void> {
  try {
    await request
  } catch (e) {
    if (!isNotFound(e)) throw e
  }
}

export function useGroups(page: number, size: number) {
  const client = useAdminApiClient()
  return useQuery({
    queryKey: ['groups', page, size],
    queryFn: () => client.listAnonymousGroups(page, size),
    placeholderData: keepPreviousData,
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
      return queryClient.invalidateQueries({ queryKey: ['groups'] })
    },
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
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['group-members', groupId] }),
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
