'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'
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

  const totalPages = Math.ceil(rowCount / pageSize)

  const [selectedRoleType, setSelectedRoleType] = useState<string>(tabValue || DEFAULT_TAB)

  const getRoles = useCallback(async () => {
    const params = setApiRequestParams(totalPages)

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
  }, [setApiRequestParams, totalPages, selectedRoleType, rowCount])

  useEffect(() => {
    getRoles()
  }, [getRoles, sorting, rowCount, pageIndex, pageSize, selectedRoleType])

  const handleRowClick = (row: Row<RoleResponse>) => {
    router.push(`/roles/${row.original.id}?_tab=${selectedRoleType}`)
  }

  return (
    <div className="w-full h-full">
      <PageHeader
        isTabHeader={true}
        tabs={[
          { value: ROLE_TYPES.SYSTEM, label: t('systemRoles') },
          { value: ROLE_TYPES.DATA, label: t('dataRoles') },
          { value: ROLE_TYPES.GOVERNANCE, label: t('governanceRoles') },
        ]}
        onClick={type => {
          setSelectedRoleType(type)
          setTabValueParam(type)
        }}
        selectedTab={selectedRoleType}
        customElement={
          <Button variant="secondary" onClick={() => router.push(`/roles/create/?_tab=${selectedRoleType}`)}>
            <Plus /> {t('newRole')}
          </Button>
        }
      />

      <SearchField searchString={search} onChangeSearchString={setSearchParam} />
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
    </div>
  )
}
export default RolesPage
