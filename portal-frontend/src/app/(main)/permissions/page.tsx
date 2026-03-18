'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { InfoBox } from '@/components/text-box/TextBox'
import { useQueryParams } from '@/hooks/use-query-params'
import { ROLE_TYPES } from '@/types/roles'

import { DataPermissionsTab } from './components/DataPermissionsTab'
import { SystemPermissionsTab } from './components/SystemPermissionsTab'

export const DEFAULT_TAB = ROLE_TYPES.SYSTEM

const PermissionsPage = () => {
  const t = useTranslations()

  const tabsValues = {
    systemPermissions: {
      value: ROLE_TYPES.SYSTEM,
      label: t('permissions.systemPermissions.title'),
      description: t('permissions.systemPermissions.description'),
    },
    dataPermissions: {
      value: ROLE_TYPES.DATA,
      label: t('permissions.dataPermissions.title'),
      description: t('permissions.dataPermissions.description'),
    },
  }

  const tabs = [tabsValues.systemPermissions, tabsValues.dataPermissions]

  const { setTabValueParam, setTotalPages, getApiRequestParamsByUrl, pageSize, tabValue } = useQueryParams()

  const [permissionType, setPermissionsType] = useState<string>(tabValue || DEFAULT_TAB)

  const requestParams = new URLSearchParams(`type=${permissionType}&${getApiRequestParamsByUrl()}`)
  const { data: permissionsData, isFetching } = useGetPermissions({ params: requestParams })

  const rowCount = permissionsData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const permissions = permissionsData?.data && !isFetching ? permissionsData.data : []

  const getSystemPermissions = () => {
    return permissions.filter(permission => permission.permissionType === ROLE_TYPES.SYSTEM)
  }

  const getDataPermissions = () => {
    return permissions.filter(permission => permission.permissionType === ROLE_TYPES.DATA)
  }

  const getTitle = () => {
    const label = tabs.find(tab => tab.value === permissionType)?.label || ''
    return t('permissions.title', { permissionType: label })
  }

  const getDescription = () => {
    return tabs.find(tab => tab.value === permissionType)?.description || ''
  }

  return (
    <PageContainer headerType="withBothTabsRows">
      <PageHeader
        title={getTitle()}
        subtitle={getDescription()}
        tabsSectionProps={{
          tabs,
          onClick: (type: string) => {
            setPermissionsType(type)
            setTabValueParam(type)
          },
          selectedTab: permissionType,
        }}
      />
      <PageBackground>
        {permissionType === ROLE_TYPES.SYSTEM ? (
          <SystemPermissionsTab permissions={getSystemPermissions()} isLoading={isFetching} />
        ) : (
          <DataPermissionsTab permissions={getDataPermissions()} isLoading={isFetching} rowCount={rowCount} />
        )}

        <div className="mt-4">
          <InfoBox text={t('permissions.infoBox')} />
        </div>
      </PageBackground>
    </PageContainer>
  )
}
export default PermissionsPage
