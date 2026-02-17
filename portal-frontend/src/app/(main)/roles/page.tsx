'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Role, ROLE_TYPES, RoleType } from '@/types/roles'

import { RolesTable } from './components/RolesTable'

export const DEFAULT_TAB: RoleType = ROLE_TYPES.SYSTEM

const RolesPage = () => {
  const t = useTranslations('roles')
  const router = useRouter()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const tabsValues = {
    systemRoles: { value: ROLE_TYPES.SYSTEM, label: t('systemRoles') },
    dataRoles: { value: ROLE_TYPES.DATA, label: t('dataRoles') },
    governanceRoles: { value: ROLE_TYPES.GOVERNANCE, label: t('governanceRoles') },
  }
  const tabs = [tabsValues.systemRoles, tabsValues.dataRoles, tabsValues.governanceRoles]

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

  const [selectedRoleType, setSelectedRoleType] = useState<string>(tabValue || DEFAULT_TAB)

  const requestParams = new URLSearchParams(`type=${selectedRoleType}&${getApiRequestParamsByUrl()}`)

  const { data: rolesData, isFetching } = useGetRoles({ params: requestParams })

  const rowCount = rolesData?.totalElements || 0
  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const handleRowClick = (row: Row<Role>) => {
    router.push(`/roles/${row.original.id}?_tab=${selectedRoleType}`)
  }

  return (
    <PageContainer headerType="withPrimaryTabs">
      <PageHeader
        title={`${t('overView')} ${tabs.find(tab => tab.value === selectedRoleType)?.label}`}
        tabsSectionProps={{
          tabs,
          onClick: type => {
            setSelectedRoleType(type)
            setTabValueParam(type)
          },
          selectedTab: selectedRoleType,
        }}
      />
      <PageBackground>
        <SearchHeader
          searchString={search}
          onChangeSearchString={setSearchParam}
          customElement={
            <Button onClick={() => router.push(`/roles/create/?_tab=${selectedRoleType}`)}>
              <Plus /> {t('newRole')}
            </Button>
          }
        />
        <TableContainer>
          <RolesTable
            roles={rolesData?.data && !isFetching ? rolesData?.data : []}
            isLoading={isFetching}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            sorting={sorting}
            onRowClick={handleRowClick}
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
export default RolesPage
