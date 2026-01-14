'use client'

import { useQuery } from '@tanstack/react-query'
import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'

import { DataSpace } from '../../../../types/dataspaces'
import { DataSpacesTable } from './components/DataSpacesTable'

const DataSpacesPage = () => {
  const t = useTranslations('dataspaces')
  const router = useRouter()

  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const { data, isLoading } = useQuery({
    queryKey: ['dataspaces', pageIndex, pageSize, sorting, search],
    queryFn: () =>
      apiRequest<DataSpace[]>({
        endpoint: '/dataspaces',
        method: 'GET',
        params: getApiRequestParamsByUrl(),
        errorMessage: 'An error occurred while fetching dataspaces.',
      }),
  })

  const rowCount = data?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize)

  const handleRowClick = (row: Row<DataSpace>) => {
    if (row.id) {
      const params = getApiRequestParamsByUrl()
      router.push(`/dataspaces/${row.original.id}?${params}`, {})
    }
  }

  const CustomElement = (
    <Button onClick={() => router.push('/dataspaces/create')}>
      <Plus />
      {t('newDataSpace')}
    </Button>
  )

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader searchString={search} onChangeSearchString={setSearchParam} customElement={CustomElement} />
        <TableContainer>
          <DataSpacesTable
            dataspaces={data?.data || []}
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

export default DataSpacesPage
