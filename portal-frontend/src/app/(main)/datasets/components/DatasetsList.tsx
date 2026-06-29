'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { useDeleteDataset } from '@/app/services/api/datasets/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
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

  const { hasPermissionInScope } = usePermissions()
  const canCreateDataset = hasPermissionInScope(PERMISSION_NAMES.DATASET_CREATE, ASSIGNMENT_SCOPE_TYPES.TENANT)

  const [datasetToDelete, setDatasetToDelete] = useState<string | null>(null)
  const [isWarningOpen, setIsWarningOpen] = useState(false)

  const { mutate: deleteDataset } = useDeleteDataset()

  const handleDeleteClick = (datasetId: string) => {
    setIsWarningOpen(true)
    setDatasetToDelete(datasetId)
  }

  const handleConfirmDelete = () => {
    if (!datasetToDelete) return

    deleteDataset(datasetToDelete, {
      onSuccess: () => {
        setDatasetToDelete(null)
        setIsWarningOpen(false)
        toast.success(t('messages.deleteSuccess'))
        router.refresh()
      },
      onError: () => {
        setDatasetToDelete(null)
        setIsWarningOpen(false)
        toast.error(t('messages.deleteError'))
      },
    })
  }

  useEffect(() => setTotalPages(Math.ceil(rowCount / pageSize) || 1), [rowCount, pageSize, setTotalPages])

  const CustomElement = canCreateDataset ? (
    <Button
      data-testid="addDatasetButton"
      onClick={() => router.push(`datasets/create?${getApiRequestParamsByUrl().toString()}`)}
    >
      <Plus />
      {t('newDataset')}
    </Button>
  ) : undefined

  return (
    <>
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
              onDeleteClick={id => handleDeleteClick(id as string)}
            />
          </TableContainer>
        </PageBackground>
      </PageContainer>

      <WarningModal
        title={t('warningModal.title')}
        description={t('warningModal.description')}
        open={isWarningOpen}
        onDiscard={() => setIsWarningOpen(false)}
        onConfirm={handleConfirmDelete}
        confirmButtonTitle={t('warningModal.confirm')}
      />
    </>
  )
}

export default DatasetsList
