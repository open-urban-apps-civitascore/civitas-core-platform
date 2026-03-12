'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/use-query-params'
import { ROLE_TYPES } from '@/types/roles'

import { DataPermissionsTab } from './components/DataPermissionsTab'
import { SystemPermissionsTab } from './components/SystemPermissionsTab'

const tabsValues = {
  systemPermissions: { value: ROLE_TYPES.SYSTEM, label: 'permissions.systemPermissions' },
  dataPermissions: { value: ROLE_TYPES.DATA, label: 'permissions.dataPermissions' },
}

export const DEFAULT_TAB = ROLE_TYPES.SYSTEM

const PermissionsPage = () => {
  const t = useTranslations()
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
    const systemPermissions = permissions.filter(permission => permission.permissionType === ROLE_TYPES.SYSTEM)
    return systemPermissions
  }

  const getDataPermissions = () => {
    const dataPermissions = permissions.filter(permission => permission.permissionType === ROLE_TYPES.DATA)
    return dataPermissions
  }

  const getTitle = () => {
    const label = tabs.find(tab => tab.value === permissionType)?.label
    const labelTranslation = label ? t(label) : ''
    return t('permissions.title', { permissionType: labelTranslation })
  }

  return (
    <PageContainer headerType="withPrimaryTabs">
      <PageHeader
        title={getTitle()}
        segmentedControlBarProps={{
          tabs,
          onTabChange: type => {
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
      </PageBackground>
    </PageContainer>
  )
}
export default PermissionsPage
