'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Permission, ROLE_TYPES } from '@/types/roles'
import { Category } from '@/types/users'

import { PermissionsTable } from './components/PermissionsTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const DEFAULT_TAB = ROLE_TYPES.SYSTEM

const PermissionsPage = () => {
  const t = useTranslations('permissions')
  const router = useRouter()

  const [permissions, setPermissions] = useState<Permission[] | []>([])
  const [categories, setCategories] = useState<Category[] | []>([])
  const [isLoading, setIsLoading] = useState(true)
  const [rowCount, setRowCount] = useState(0)
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const tabsValues = {
    systemPermissions: { value: ROLE_TYPES.SYSTEM, label: t('systemPermissions') },
    dataPermissions: { value: ROLE_TYPES.DATA, label: t('dataPermissions') },
    governancePermissions: { value: ROLE_TYPES.GOVERNANCE, label: t('governancePermissions') },
  }
  const tabs = [tabsValues.systemPermissions, tabsValues.dataPermissions, tabsValues.governancePermissions]

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setApiRequestParams,
    setTabValueParam,
    pageIndex,
    pageSize,
    sorting,
    search,
    tabValue,
  } = useQueryParams()

  const [permissionType, setPermissionsType] = useState<string>(tabValue || DEFAULT_TAB)
  const totalPages = Math.ceil(rowCount / pageSize)

  const getPermissions = async () => {
    const params = setApiRequestParams(totalPages)

    try {
      setIsLoading(true)
      const [permissionResponse, categoriesResponse] = await Promise.all([
        fetch(`${URL}/permissions?type=${permissionType}&${params.toString()}`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/categories`, {
          cache: 'no-store',
        }),
      ])
      if (!permissionResponse || !categoriesResponse) {
        throw new Error('An error occurred while loading permissions data')
      }

      const [permissionsData, categoriesData]: [Permission[], Category[]] = await Promise.all([
        permissionResponse.json(),
        categoriesResponse.json(),
      ])

      setPermissions(permissionsData)
      setCategories(categoriesData)
      setIsLoading(false)

      const totalCount = Number(permissionResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error(error)
      setIsLoading(false)
      throw new Error('An error occurred while loading permissions data')
    }
  }

  useEffect(() => {
    getPermissions()
  }, [sorting, rowCount, pageIndex, pageSize, permissionType, search])

  const handleOpenButtonClick = (row: Row<Permission>) => {
    console.log(`/permissions/${row.original.id}?_tab=${permissionType}`)
  }

  return (
    <PageContainer headerType="withPrimaryTabs">
      <PageHeader
        title={tabs.find(tab => tab.value === permissionType)?.label}
        tabs={{
          tabs,
          onClick: type => {
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
            permissions={permissions}
            isLoading={isLoading}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            sorting={sorting}
            onOpenButtonClick={handleOpenButtonClick}
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
