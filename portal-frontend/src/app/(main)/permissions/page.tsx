'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/use-query-params'
import { ROLE_TYPES } from '@/types/roles'

import { PermissionsTable } from './components/PermissionsTable'

const tabsValues = {
  systemPermissions: { value: ROLE_TYPES.SYSTEM, label: 'permissions.systemPermissions' },
  dataPermissions: { value: ROLE_TYPES.DATA, label: 'permissions.dataPermissions' },
}

export const DEFAULT_TAB = ROLE_TYPES.SYSTEM

const PermissionsPage = () => {
  const t = useTranslations()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const tabs = [tabsValues.systemPermissions, tabsValues.dataPermissions]

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    setTabValueParam,
    setTotalPages,
    pageIndex,
    pageSize,
    sorting,
    search,
    tabValue,
    totalPages,
  } = useQueryParams()

  const [permissionType, setPermissionsType] = useState<string>(tabValue || DEFAULT_TAB)

  const requestParams = new URLSearchParams(`type=${permissionType}&${getApiRequestParamsByUrl()}`)
  const { data: permissionsData, isFetching } = useGetPermissions({ params: requestParams })

  const rowCount = permissionsData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

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
        <SearchHeader searchString={search} onChangeSearchString={setSearchParam} />
        <TableContainer>
          <PermissionsTable
            permissions={permissionsData?.data && !isFetching ? permissionsData?.data : []}
            isLoading={isFetching}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            sorting={sorting}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}
export default PermissionsPage
