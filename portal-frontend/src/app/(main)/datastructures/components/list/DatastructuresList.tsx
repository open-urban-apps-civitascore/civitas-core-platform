'use client'

import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { DatastructuresListData } from '@/types/datastructures'

import { DatastructuresTable } from './DatastructuresTable'

interface DatastructuresListProps {
  datastructures: DatastructuresListData[]
  rowCount: number
}

export const DatastructuresList = (props: DatastructuresListProps) => {
  const { datastructures, rowCount } = props
  const t = useTranslations('datastructures')
  const {
    pageIndex,
    pageSize,
    search,
    sorting,
    totalPages,
    setPaginationParams,
    setSearchParam,
    setSortingParams,
    setTotalPages,
  } = useQueryParams()

  useEffect(() => setTotalPages(Math.ceil(rowCount / pageSize) || 1), [rowCount, pageSize, setTotalPages])

  const CustomElement = (
    <Button data-testid="addDatasourceButton" onClick={() => console.log('datastructures/create')}>
      <Plus />
      {t('newDatasource')}
    </Button>
  )

  return (
    <PageContainer headerType="onlyTitle" testId="datastructuresPage">
      <PageHeader title={t('title')} />
      <PageBackground>
        <TableContainer>
          <SearchHeader customElement={CustomElement} onChangeSearchString={setSearchParam} searchString={search} />
          <DatastructuresTable
            datastructures={datastructures}
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
