import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { Group } from '@/types/groups'
import { User } from '@/types/users'

export const useGroupMembership = (userData: User) => {
  const queryClient = useQueryClient()
  const [isSavingGroups, setIsSavingGroups] = useState(false)

  const getCachedGroupMembers = useCallback(
    (groupId: string): string[] => {
      const queries = queryClient.getQueriesData<{ data: Group[] }>({ queryKey: ['groups'] })
      for (const [, data] of queries) {
        const group = data?.data?.find(g => g.id === groupId)
        if (group) return group.members?.map(m => m.id) || []
      }
      return []
    },
    [queryClient],
  )

  const saveGroupMemberships = useCallback(
    async (userId: string, currentGroupIds: string[]) => {
      const originalGroupIds = userData?.groups?.map(g => g.id) || []

      const addedGroupIds = currentGroupIds.filter(id => !originalGroupIds.includes(id))
      const removedGroupIds = originalGroupIds.filter(id => !currentGroupIds.includes(id))

      if (addedGroupIds.length === 0 && removedGroupIds.length === 0) return

      setIsSavingGroups(true)
      try {
        const groupPatches: Promise<unknown>[] = []

        const getMemberIds = async (groupId: string) => {
          const cached = getCachedGroupMembers(groupId)
          if (cached.length > 0) return cached
          const { data } = await apiRequest<Group>({
            endpoint: `/groups/${groupId}`,
            method: 'GET',
            headers: { 'x-api-request': 'true' },
          })
          return data.members?.map(m => m.id) || []
        }

        for (const groupId of removedGroupIds) {
          const memberIds = await getMemberIds(groupId)
          groupPatches.push(
            apiRequest({
              endpoint: `/groups/${groupId}`,
              method: 'PATCH',
              headers: { 'x-api-request': 'true' },
              data: { memberIds: memberIds.filter(id => id !== userId) },
            }),
          )
        }

        for (const groupId of addedGroupIds) {
          const memberIds = await getMemberIds(groupId)
          if (!memberIds.includes(userId)) {
            groupPatches.push(
              apiRequest({
                endpoint: `/groups/${groupId}`,
                method: 'PATCH',
                headers: { 'x-api-request': 'true' },
                data: { memberIds: [...memberIds, userId] },
              }),
            )
          }
        }

        await Promise.all(groupPatches)
      } finally {
        setIsSavingGroups(false)
      }
    },
    [userData, getCachedGroupMembers],
  )

  return { saveGroupMemberships, isSavingGroups }
}
