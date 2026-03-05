'use client'

import { useTranslations } from 'next-intl'
import { useMemo } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { cn } from '@/lib/utils'
import { ROLE_TYPES } from '@/types/roles'

import { mapRolesData } from '../../utils/mappers'
import { RoleCategory } from './RoleCategory'

interface RolesTabProps {
  groupIds: string[]
}

export const RolesTab = (props: RolesTabProps) => {
  const { groupIds } = props
  const t = useTranslations()

  const groupsRequestParams = new URLSearchParams(groupIds.map(id => `id=${id}`).join('&'))
  const {
    data: groupsData,
    isFetching: isLoadingGroups,
    error: groupsError,
  } = useGetGroups({ params: groupsRequestParams, isEnabled: groupIds.length > 0 })

  const roleIds = useMemo(() => new Set(groupsData?.data.flatMap(group => group.roles)), [groupsData])
  const rolesRequestparams = new URLSearchParams([...roleIds].map(role => `id=${role}`).join('&'))
  const {
    data: rolesData,
    isLoading: areRolesLoading,
    error: rolesError,
  } = useGetRoles({ params: rolesRequestparams, isEnabled: roleIds.size > 0 })

  const roles = useMemo(
    () => (rolesData && groupsData ? mapRolesData(rolesData.data, groupsData.data) : []),
    [rolesData, groupsData],
  )

  const error = groupsError || rolesError
  const isLoading = isLoadingGroups || areRolesLoading

  return (
    <PageBackground>
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
              className="border-b-0"
            />
          </>
        )}
        {isLoading && <LoadingSpinner className="h-full" />}
        {error && <p className="h-full flex items-center justify-center">{t('common.errors.loadingError')}</p>}
      </ContentCard>
    </PageBackground>
  )
}
