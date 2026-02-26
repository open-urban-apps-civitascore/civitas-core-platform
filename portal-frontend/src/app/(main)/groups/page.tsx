'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group } from '@/types/groups'

import { GroupsTable } from './components/GroupsTable'

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
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    setTotalPages,
    pageIndex,
    pageSize,
    sorting,
    search,
    totalPages,
  } = useQueryParams()

  const { data: groupsData, isFetching } = useGetGroups({ params: getApiRequestParamsByUrl() })

  const rowCount = groupsData?.totalElements || 0
  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  const handleRowClick = (row: Row<Group>) => {
    const params = getApiRequestParamsByUrl()
    router.push(`groups/${row.id}?${params}`)
  }

  const CustomElement = () => {
    const params = getApiRequestParamsByUrl()
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
            groups={groupsData?.data && !isFetching ? groupsData?.data : []}
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
            isLoading={isFetching}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default GroupsPage
