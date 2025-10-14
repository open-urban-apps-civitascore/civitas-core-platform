'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { GroupResponse } from '@/types/groups'

import GroupsTable from './components/GroupsTable'

export const getSortParam = (sorting: SortingState) => {
  if (sorting.length > 0) {
    const sortingId = sorting[0]?.id
    const sortParam = `&_sort=${sortingId}`
    const orderParam = sorting[0]?.desc ? `&_order=desc` : `&_order=asc`
    return `${sortParam}${orderParam}`
  }
  return ''
}

const GroupsPage = () => {
  const t = useTranslations('groups')
  const router = useRouter()
  const [groups, setGroups] = useState<GroupResponse[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [isLoading, setIsLoading] = useState(true)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setApiRequestParams,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  const getGroupsData = async () => {
    const params = setApiRequestParams(totalPages)

    try {
      setIsLoading(true)
      const groupsResponse = await fetch(`${URL}/groups?${params.toString()}`, {
        cache: 'no-store',
      })

      if (!groupsResponse) {
        throw new Error('An error occurred while loading user groups data')
      }

      const groupsData: GroupResponse[] = await groupsResponse.json()

      setGroups(groupsData)
      setIsLoading(false)

      const totalCount = Number(groupsResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error(error)
      setIsLoading(false)
      throw new Error('An error occurred while loading groups data')
    }
  }

  useEffect(() => {
    getGroupsData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, totalPages, setApiRequestParams])

  const handleRowClick = (row: Row<GroupResponse>) => {
    if (row.id) {
      const params = setApiRequestParams(totalPages)
      router.push(`groups/${row.id}?${params}`)
    }
  }

  const CustomElement = () => {
    const params = setApiRequestParams(totalPages)
    return (
      <Button onClick={() => router.push(`groups/create?${params}`)}>
        <Plus />
        {t('newGroup')}
      </Button>
    )
  }

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader
          customElement={<CustomElement />}
          onChangeSearchString={setSearchParam}
          searchString={search}
          placeholder={t('search')}
        />
        <TableContainer>
          <GroupsTable
            groups={groups}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onRowClick={handleRowClick}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
            isLoading={isLoading}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default GroupsPage
