'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'

import { ROLE_TYPES, RoleResponse } from '../../../../types/roles'
import { RolesTable } from './components/RolesTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const DEFAULT_TAB = ROLE_TYPES.SYSTEM

const RolesPage = () => {
  const t = useTranslations('roles')
  const router = useRouter()

  const [listRoles, setListRoles] = useState<RoleResponse[] | []>([])
  const [isLoading, setIsLoading] = useState(true)
  const [rowCount, setRowCount] = useState(0)
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
    pageIndex,
    pageSize,
    sorting,
    search,
    tabValue,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  useEffect(() => {
    if (totalPages && totalPages > 0 && pageIndex + 1 > totalPages) {
      setPaginationParams({ pageIndex: totalPages - 1, pageSize: pageSize })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [totalPages, pageIndex, pageSize])

  const [selectedRoleType, setSelectedRoleType] = useState<string>(tabValue || DEFAULT_TAB)
  const [selectedSubTab, setSelectedSubTab] = useState<string>(tabValue || 'roles')

  const getRoles = useCallback(async () => {
    const params = getApiRequestParamsByUrl()

    try {
      setIsLoading(true)

      const rolesResponse = await fetch(`${URL}/roles?type=${selectedRoleType}&${params.toString()}`)
      const rolesData: RoleResponse[] = await rolesResponse.json()
      setListRoles(rolesData)

      const totalCount = Number(rolesResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }

      setIsLoading(false)
    } catch (error) {
      console.error(error)

      setIsLoading(false)
    }
  }, [getApiRequestParamsByUrl, selectedRoleType, rowCount])

  useEffect(() => {
    getRoles()
  }, [getRoles, sorting, rowCount, pageIndex, pageSize, selectedRoleType])

  const handleRowClick = (row: Row<RoleResponse>) => {
    router.push(`/roles/${row.original.id}?_tab=${selectedRoleType}`)
  }

  return (
    <PageContainer headerType="withBothTabsRows">
      <PageHeader
        title={tabs.find(tab => tab.value === selectedRoleType)?.label}
        tabs={{
          tabs,
          onClick: type => {
            setSelectedRoleType(type)
            setTabValueParam(type)
          },
          selectedTab: selectedRoleType,
        }}
        subTabs={{
          tabs: [
            { value: 'roles', label: t('roles') },
            { value: 'roleSets', label: t('roleSets') },
          ],
          selectedTab: selectedSubTab,
          onClick: newTab => setSelectedSubTab(newTab),
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
            roles={listRoles}
            isLoading={isLoading}
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
