'use client'

import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { useMemo } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { cn } from '@/lib/utils'
import { Group } from '@/types/groups'
import { BaseRole, ROLE_TYPES } from '@/types/roles'

import { RoleCategory } from './RoleCategory'

interface RolesTabProps {
  groupIds: string[]
}

export const RolesTab = (props: RolesTabProps) => {
  const { groupIds } = props
  const t = useTranslations()

  const {
    data: groupsData,
    isLoading: areGroupsLoading,
    error: groupsError,
  } = useQuery({
    queryKey: ['groups', groupIds],
    queryFn: () =>
      apiRequest<Group[]>({
        method: 'GET',
        endpoint: `/groups?${groupIds.map(id => `id=${id}`).join('&')}`,
        errorMessage: 'An error occurred while fetching groups data.',
      }),
    enabled: groupIds.length > 0,
  })

  const roleIds = useMemo(() => new Set(groupsData?.data.flatMap(group => group.roles)), [groupsData])

  const {
    data: rolesData,
    isLoading: areRolesLoading,
    error: rolesError,
  } = useQuery({
    queryKey: ['roles', groupIds],
    queryFn: () =>
      apiRequest<BaseRole[]>({
        method: 'GET',
        endpoint: `/roles?${[...roleIds].map(role => `id=${role}`).join('&')}`,
        errorMessage: 'An error occurred while fetching roles data.',
      }),
    enabled: roleIds.size > 0,
  })

  const mapRolesData = (roles: BaseRole[], groupData: Group[]) => {
    const allRoles = groupData.flatMap(group =>
      group.roles.flatMap(groupRole => {
        const currentRole = roles.find(role => role.id === groupRole)
        if (!currentRole) return []
        return {
          id: `${group.id}-${currentRole.id}`,
          name: currentRole.name,
          inherited: false,
          group: group?.title || null,
          dataspace: group?.dataspace || null,
          type: currentRole.type,
        }
      }),
    )
    return allRoles
  }

  const roles = useMemo(
    () => (rolesData && groupsData ? mapRolesData(rolesData.data, groupsData.data) : []),
    [rolesData, groupsData],
  )

  const error = groupsError || rolesError
  const isLoading = areGroupsLoading || areRolesLoading

  return (
    <ContentCard className={cn((error || isLoading) && 'h-50')}>
      {!error && !isLoading && (
        <>
          <DetailsFieldContainer isTitleField>
            <h2>{t('users.roles.title')}</h2>
          </DetailsFieldContainer>
          <RoleCategory
            title={t('roles.systemRoles')}
            rolesType={ROLE_TYPES.SYSTEM}
            roles={roles.filter(role => role.type === ROLE_TYPES.SYSTEM)}
            isLoading={isLoading}
          />

          <RoleCategory
            title={t('roles.dataRoles')}
            rolesType={ROLE_TYPES.DATA}
            roles={roles.filter(role => role.type === ROLE_TYPES.DATA)}
            isLoading={isLoading}
          />

          <RoleCategory
            title={t('roles.governanceRoles')}
            rolesType={ROLE_TYPES.GOVERNANCE}
            roles={roles.filter(role => role.type === ROLE_TYPES.GOVERNANCE)}
            isLoading={isLoading}
            className="border-b-0"
          />
        </>
      )}
      {isLoading && <LoadingSpinner className="h-full" />}
      {error && <p className="h-full flex items-center justify-center">{t('common.errors.loadingError')}</p>}
    </ContentCard>
  )
}
