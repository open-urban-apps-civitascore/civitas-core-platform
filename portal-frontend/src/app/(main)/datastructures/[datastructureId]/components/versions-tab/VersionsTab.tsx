'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { DatastructureVersionsListData } from '@/types/datastructures'

import { VersionsTable } from './VersionsTable'

interface VersionsTabProps {
  datastructureId: string
  versions: DatastructureVersionsListData[]
  isReadOnly: boolean
  rowCount: number
}

export const VersionsTab = (props: VersionsTabProps) => {
  const { datastructureId, versions, rowCount, isReadOnly } = props
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
    <Button
      data-testid="addVersionButton"
      onClick={() => router.push(`/datastructures/${datastructureId}/createVersion?mode=edit`)}
    >
      <Plus />
      {t('newVersion')}
    </Button>
  )

  return (
    <TableContainer>
      <SearchHeader
        customElement={!isReadOnly && CustomElement}
        onChangeSearchString={setSearchParam}
        searchString={search}
      />
      <VersionsTable
        datastructureId={datastructureId}
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
