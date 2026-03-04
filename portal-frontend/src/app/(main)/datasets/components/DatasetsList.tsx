'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Dataset } from '@/types/datasets'

import { DatasetsTable } from './DatasetsTable'

type DatasetsListProps = {
  datasets: Dataset[]
  rowCount: number
}

const DatasetsList = (props: DatasetsListProps) => {
  const { datasets, rowCount } = props
  const t = useTranslations('datasets')
  const router = useRouter()

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

  useEffect(() => setTotalPages(Math.ceil(rowCount / pageSize) || 1), [rowCount, pageSize, setTotalPages])

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
            datasets={datasets}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            onPaginationChange={setPaginationParams}
            onSortingChange={setSortingParams}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default DatasetsList
