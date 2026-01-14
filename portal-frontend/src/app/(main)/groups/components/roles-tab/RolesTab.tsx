'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { Group, GroupTabProps } from '@/types/groups'
import { BaseRole, ROLE_TYPES } from '@/types/roles'

import { RoleCategory } from './RoleCategory'

export const RolesTab = (props: GroupTabProps) => {
  const { groupData } = props
  const t = useTranslations('groups')
  const tRoles = useTranslations('roles')
  const router = useRouter()
  const queryClient = useQueryClient()

  const [originalRoles, setOriginalRoles] = useState(groupData.roles)
  const [group, setGroup] = useState<Group>(groupData)

  const { data: rolesdata, isLoading: areRolesLoading } = useQuery({
    queryKey: ['roles'],
    queryFn: () =>
      apiRequest<BaseRole[]>({
        endpoint: '/roles',
        method: 'GET',
        errorMessage: 'An error occurred while fetching roles.',
      }),
  })

  const updateGroupMutation = useMutation({
    mutationFn: (data: Group) =>
      apiRequest<Group>({
        method: 'PUT',
        endpoint: `/groups/${data.id}`,
        data: data,
        errorMessage: 'An error occurred while updating the group.',
      }),
    onSuccess: ({ data }) => {
      setOriginalRoles(data.roles)
      queryClient.invalidateQueries({
        queryKey: ['groups', data.id],
      })
    },
    onError: error => {
      console.error('An error occurred while updating the group. ', error)
    },
  })

  const hasChanges = useMemo(
    () => JSON.stringify(group.roles.sort()) !== JSON.stringify(originalRoles.sort()),
    [group, originalRoles],
  )

  const handleAddRole = (roleId: string) => {
    const updatedRoles = [...group.roles, roleId]
    setGroup({ ...group, roles: updatedRoles })
  }

  const handleRemoveRole = (roleId: string) => {
    const updatedRoles = group.roles.filter(id => id !== roleId)
    setGroup({ ...group, roles: updatedRoles })
  }

  const handleUpdateGroup = async () => {
    updateGroupMutation.mutate(group)
  }

  if (areRolesLoading || updateGroupMutation.isPending) {
    return <LoadingSpinner className="h-full" />
  }

  if (!group) {
    return (
      <div className="p-8">
        <p>{t('roles.userNotFound')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col justify-between h-full">
      <ContentCard>
        <DetailsFieldContainer isTitleField>
          <h2>{t('roles.groupInfo')}</h2>
        </DetailsFieldContainer>
        <RoleCategory
          title={tRoles('systemRoles')}
          category={ROLE_TYPES.SYSTEM}
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={rolesdata?.data || []}
        />

        <RoleCategory
          title={tRoles('dataRoles')}
          category={ROLE_TYPES.DATA}
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={rolesdata?.data || []}
        />

        <RoleCategory
          title={tRoles('governanceRoles')}
          category={ROLE_TYPES.GOVERNANCE}
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={rolesdata?.data || []}
          className="border-0"
        />
      </ContentCard>
      <ActionButtons
        onCancelClick={() => router.push('/groups')}
        confirmButtonType="button"
        isConfirmButtonDisabled={!hasChanges}
        onConfirmClick={handleUpdateGroup}
      />
    </div>
  )
}
