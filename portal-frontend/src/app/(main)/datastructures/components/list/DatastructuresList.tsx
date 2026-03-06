'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { DatastructuresListData } from '@/types/datastructures'

import { DatastructuresTable } from './DatastructuresTable'
import { useDeleteDatastructure } from '@/app/services/api/datastructures/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { toast } from 'sonner'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'

interface DatastructuresListProps {
  datastructures: DatastructuresListData[]
  rowCount: number
}

export const DatastructuresList = (props: DatastructuresListProps) => {
  const { datastructures, rowCount } = props
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const [datastructureToDelete, setDatastructureToDelete] = useState<string | null>(null)
  const [isDeletionInfoModalOpen, setIsDeletionInfoModalOpen] = useState(false)
  const [isDeletionWarningModalOpen, setIsDeletionWarningModalOpen] = useState(false)
  const deleteDatastructure = useDeleteDatastructure()
  const isLoading = deleteDatastructure.isPending
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

  const handleDeleteDatastructure = () => {
    if (datastructureToDelete) {
      deleteDatastructure.mutate(datastructureToDelete, {
        onSuccess: () => {
          toast.success(tCommon('success.deletionSuccess', { item: tCommon('items.datastructure') }))
          router.refresh()
        },
        onError: () => {
          toast.error(t('errors.deletionError'))
        },
        onSettled: () => {
          setIsDeletionWarningModalOpen(false)
          setDatastructureToDelete(null)
        },
      })
    }
  }

  const handleDeleteDatastructureClick = (id: string) => {
    const datastructure = datastructures.find(datastructure => datastructure.id === id)
    if (!datastructure) return
    if (datastructure.inUse) setIsDeletionInfoModalOpen(true)
    else {
      setDatastructureToDelete(id)
      setIsDeletionWarningModalOpen(true)
    }
  }

  const handleDiscardDeletion = () => {
    setDatastructureToDelete(null)
    setIsDeletionWarningModalOpen(false)
  }

  const CustomElement = (
    <Button data-testid="addDatasourceButton" onClick={() => router.push('datastructures/create')}>
      <Plus />
      {t('newDatasource')}
    </Button>
  )

  return (
    <PageContainer headerType="withSubTabsOrSubtitle" testId="datastructuresPage">
      <PageHeader title={t('title')} subtitle={t('subtitle')} />
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
            onDeleteDatastructureClick={handleDeleteDatastructureClick}
          />
        </TableContainer>
      </PageBackground>
      <InfoModal
        open={isDeletionInfoModalOpen}
        title={t('infoModal.title')}
        description={t('infoModal.description')}
        onClose={() => setIsDeletionInfoModalOpen(false)}
      />
      <WarningModal
        open={isDeletionWarningModalOpen}
        title={tCommon('deletionWarningModal.title', { item: tCommon('items.datastructure') })}
        description={tCommon('deletionWarningModal.description')}
        onDiscard={handleDiscardDeletion}
        confirmButtonTitle={tCommon('actions.delete')}
        onConfirm={handleDeleteDatastructure}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
