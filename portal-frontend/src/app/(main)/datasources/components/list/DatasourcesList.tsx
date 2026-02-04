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
import { Datasource } from '@/types/datasources'

import { DatasourcesTable } from './DatasourcesTable'

interface DatasourcesListProps {
  datasources: Datasource[]
  rowCount: number
}

export const DatasourcesList = (props: DatasourcesListProps) => {
  const { datasources, rowCount } = props
  const t = useTranslations('datasources')
  const router = useRouter()
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
    <Button data-testid="addDatasourceButton" onClick={() => router.push('/datasources/create')}>
      <Plus />
      {t('newDatasource')}
    </Button>
  )

  return (
    <PageContainer headerType="onlyTitle" testId="datasourcesPage">
      <PageHeader title={t('title')} />
      <PageBackground>
        <TableContainer>
          <SearchHeader customElement={CustomElement} onChangeSearchString={setSearchParam} searchString={search} />
          <DatasourcesTable
            datasources={datasources}
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
