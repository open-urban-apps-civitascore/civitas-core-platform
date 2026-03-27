'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Role, ROLE_TYPES, RoleType } from '@/types/roles'

import { RolesTable } from './components/RolesTable'

export const DEFAULT_TAB: RoleType = ROLE_TYPES.SYSTEM

const RolesPage = () => {
  const t = useTranslations('roles')
  const router = useRouter()
  const { hasPermission } = usePermissions()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const tabsValues = {
    systemRoles: { value: ROLE_TYPES.SYSTEM, label: t('systemRoles') },
    dataRoles: { value: ROLE_TYPES.DATA, label: t('dataRoles') },
  }
  const tabs = [tabsValues.systemRoles, tabsValues.dataRoles]

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setTabValueParam,
    getApiRequestParamsByUrl,
    pageIndex,
    pageSize,
    sorting,
    search,
    tabValue,
  } = useQueryParams()

  const [selectedRoleType, setSelectedRoleType] = useState<string>(tabValue || DEFAULT_TAB)

  const requestParams = useMemo(() => {
    const params = getApiRequestParamsByUrl()
    params.set('roleType', selectedRoleType)
    return params
  }, [getApiRequestParamsByUrl, selectedRoleType])

  const { data: rolesData, isFetching } = useGetRoles({ params: requestParams })

  const rowCount = rolesData?.totalElements || 0
  const totalPages = rolesData?.totalPages || 0

  return (
    <PageContainer headerType="withPrimaryTabs">
      <PageHeader
        title={`${t('overView')} ${tabs.find(tab => tab.value === selectedRoleType)?.label}`}
        tabsSectionProps={{
          tabs,
          onClick: type => {
            setSelectedRoleType(type)
            setTabValueParam(type)
            setPaginationParams({ pageIndex: 0, pageSize })
          },
          selectedTab: selectedRoleType,
        }}
      />
      <PageBackground>
        <SearchHeader
          searchString={search}
          onChangeSearchString={setSearchParam}
          customElement={
            hasPermission(PERMISSION_NAMES.ROLE_CREATE) ? (
              <Button onClick={() => router.push(`/roles/create/?tab=${selectedRoleType}`)}>
                <Plus /> {selectedRoleType === ROLE_TYPES.SYSTEM ? t('newSystemRole') : t('newDataRole')}
              </Button>
            ) : undefined
          }
        />
        <TableContainer>
          <RolesTable
            roles={isFetching ? [] : rolesData?.data || []}
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
            selectedRoleType={selectedRoleType}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}
export default RolesPage
