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
import { DatastructuresListData, DatastructureVersionsListData } from '@/types/datastructures'

import { DatastructuresTable } from './DatastructuresTable'
import { VersionsTable } from './VersionsTable'
import { ContentCard } from '@/components/content-card/ContentCard'

interface VersionsTabProps {
  versions: DatastructureVersionsListData[]
  rowCount: number
}

export const VersionsTab = (props: VersionsTabProps) => {
  const { versions, rowCount } = props
  const t = useTranslations('datastructures')
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
    <Button data-testid="addVersionButton" onClick={() => router.push('datastructures/versions/create')}>
      <Plus />
      {t('newVersion')}
    </Button>
  )

  return (
    <TableContainer>
      <SearchHeader customElement={CustomElement} onChangeSearchString={setSearchParam} searchString={search} />
      <VersionsTable
        versions={versions}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        sorting={sorting}
        totalPages={totalPages}
        onPaginationChange={setPaginationParams}
        onSortingChange={setSortingParams}
      />
    </TableContainer>
  )
}
