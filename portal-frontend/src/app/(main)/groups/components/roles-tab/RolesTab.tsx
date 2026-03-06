'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { usePatchGroup } from '@/app/services/api/groups/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { Group } from '@/types/groups'
import { ROLE_TYPES } from '@/types/roles'

import { RoleCategory } from './RoleCategory'

interface RolesTabProps {
  groupData: Group
}

export const RolesTab = (props: RolesTabProps) => {
  const { groupData } = props
  const t = useTranslations('groups')
  const tRoles = useTranslations('roles')
  const router = useRouter()
  const updateGroup = usePatchGroup()
  const originalRoleIds = useMemo<string[]>(
    () => groupData.assignments?.map(assignment => assignment.role.id) ?? [],
    [groupData.assignments],
  )
  const [roles, setRoles] = useState<string[]>(originalRoleIds)

  const { data: rolesdata, isLoading: isLoadingRoles } = useGetRoles()

  const hasChanges = useMemo(() => {
    const sortedRoles = [...roles].sort()
    const sortedOriginal = [...originalRoleIds].sort()
    return JSON.stringify(sortedRoles) !== JSON.stringify(sortedOriginal)
  }, [roles, originalRoleIds])

  const handleAddRole = (roleId: string) => {
    const updatedRoles = [...roles, roleId]
    setRoles(updatedRoles)
  }

  const handleRemoveRole = (roleId: string) => {
    const updatedRoles = roles.filter(id => id !== roleId)
    setRoles(updatedRoles)
  }

  const handleUpdateGroup = async () => {
    updateGroup.mutate({ roleIds: roles, id: groupData.id }, { onSuccess: () => router.refresh() })
  }

  if (isLoadingRoles || updateGroup.isPending) {
    return <LoadingSpinner className="h-full" />
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
          groupRoles={roles}
          allRoles={rolesdata?.data || []}
        />

        <RoleCategory
          title={tRoles('dataRoles')}
          category={ROLE_TYPES.DATA}
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={roles}
          allRoles={rolesdata?.data || []}
        />

        <RoleCategory
          title={tRoles('governanceRoles')}
          category={ROLE_TYPES.GOVERNANCE}
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={roles}
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
