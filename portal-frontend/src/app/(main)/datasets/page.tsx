'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetDatasets } from '@/app/services/api/datasets/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'

import { DatasetsTable } from './components/DatasetsTable'

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

  const { data: datasetsData, isFetching } = useGetDatasets({ params: getApiRequestParamsByUrl() })

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
        <SearchHeader
          searchString={search}
          onChangeSearchString={setSearchParam}
          aria-label={t('searchDatasets')}
          customElement={CustomElement}
        />
        <TableContainer>
          <DatasetsTable
            datasets={datasetsData?.data && !isFetching ? datasetsData.data : []}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            onPaginationChange={setPaginationParams}
            onSortingChange={setSortingParams}
            isLoading={isFetching}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default DatasetsPage
