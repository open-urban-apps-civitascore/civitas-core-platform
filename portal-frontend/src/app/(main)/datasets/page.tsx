'use client'

import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { DatasetResponse } from '@/types/datasets'

import DatasetsTable from './components/DatasetsTable'

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const router = useRouter()
  const [rowCount, setRowCount] = useState(0)

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

  const params = useMemo(
    () => getApiRequestParamsByUrl().toString(),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [pageIndex, pageSize, URL, sorting, search, getApiRequestParamsByUrl],
  )

  const {
    data: datasetsData,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['datasets', params],
    queryFn: () =>
      apiRequest<DatasetResponse[]>({
        method: 'GET',
        endpoint: '/datasets',
        params: getApiRequestParamsByUrl(),
        errorMessage: 'An error occurred while fetching datasets.',
      }),
  })

  useEffect(() => {
    if (datasetsData) {
      const totalCount = datasetsData.totalElements || 0
      setRowCount(totalCount)
      setTotalPages(Math.ceil(totalCount / pageSize))
    }
  }, [datasetsData, pageSize, setTotalPages])

  const CustomElement = (
    <Button onClick={() => router.push(`datasets/create?${getApiRequestParamsByUrl().toString()}`)}>
      <Plus />
      {t('newDataset')}
    </Button>
  )

  return (
    <PageContainer testId="datasetsPage" headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        {!error && !isLoading && (
          <>
            <SearchHeader
              searchString={search}
              onChangeSearchString={setSearchParam}
              aria-label={t('searchDatasets')}
              customElement={CustomElement}
            />
            <TableContainer>
              <DatasetsTable
                datasets={datasetsData?.data || []}
                rowCount={rowCount}
                pageIndex={pageIndex}
                pageSize={pageSize}
                sorting={sorting}
                totalPages={totalPages}
                onPaginationChange={setPaginationParams}
                onSortingChange={setSortingParams}
                isLoading={isLoading}
              />
            </TableContainer>
          </>
        )}
      </PageBackground>
    </PageContainer>
  )
}

export default DatasetsPage
