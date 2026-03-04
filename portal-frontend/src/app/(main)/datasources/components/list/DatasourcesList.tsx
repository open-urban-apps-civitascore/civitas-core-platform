'use client'

import { SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { useDeleteDatasource } from '@/app/services/api/datasources/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { Button } from '@/components/ui/button'
import { useTableSearchParams } from '@/hooks/use-table-search-params'
import { STATUS_TYPES } from '@/types/common'
import { Datasource } from '@/types/datasources'

import { DatasourcesTable } from './DatasourcesTable'

interface DatasourcesListProps {
  datasources: Datasource[]
  totalCount: number
  pageIndex: number
  pageSize: number
  sorting: SortingState
  totalPages: number
  search: string
}

export const DatasourcesList = ({
  datasources,
  totalCount,
  pageIndex,
  pageSize,
  sorting,
  totalPages,
  search,
}: DatasourcesListProps) => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const router = useRouter()

  const [datasourceToDelete, setDatasourceToDelete] = useState<Datasource | null>(null)
  const deleteDatasource = useDeleteDatasource()
  const { handleSortingChange, handlePaginationChange, handleSearchChange } = useTableSearchParams()

  const handleDeleteConfirm = () => {
    if (datasourceToDelete) {
      deleteDatasource.mutate(datasourceToDelete.id, {
        onSuccess: () => router.refresh(),
      })
    }
    setDatasourceToDelete(null)
  }

  const CustomElement = (
    <Button data-testid="addDatasourceButton" onClick={() => router.push('/datasources/create')}>
      <Plus />
      {t('newDatasource')}
    </Button>
  )

  return (
    <>
      <SearchHeader customElement={CustomElement} onChangeSearchString={handleSearchChange} searchString={search} />
      <DatasourcesTable
        datasources={datasources}
        rowCount={totalCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        sorting={sorting}
        totalPages={totalPages}
        onSortingChange={handleSortingChange}
        onPaginationChange={handlePaginationChange}
        onDelete={setDatasourceToDelete}
      />
      {datasourceToDelete?.dataSourceStatus === STATUS_TYPES.AVAILABLE ? (
        <WarningModal
          title={t('availableModal.title')}
          description={t('availableModal.description')}
          open={!!datasourceToDelete}
          onOpenChange={() => setDatasourceToDelete(null)}
          onDiscard={() => setDatasourceToDelete(null)}
        />
      ) : (
        <WarningModal
          title={t('deleteModal.title')}
          description={t('deleteModal.description')}
          open={!!datasourceToDelete}
          confirmButtonTitle={tCommon('actions.delete')}
          onOpenChange={() => setDatasourceToDelete(null)}
          onDiscard={() => setDatasourceToDelete(null)}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </>
  )
}
