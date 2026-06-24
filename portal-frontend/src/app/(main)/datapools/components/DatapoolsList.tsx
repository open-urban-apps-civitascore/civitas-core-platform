'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { useDeleteDatapool } from '@/app/services/api/datapools/clientRequests'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
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
import { DatapoolSummary } from '@/types/datapools'

import { DatapoolsTable } from './DatapoolsTable'

type DatapoolsListProps = {
  datapools: DatapoolSummary[]
  rowCount: number
}

const DatapoolsList = (props: DatapoolsListProps) => {
  const { datapools, rowCount } = props
  const t = useTranslations('datapools')
  const tCommon = useTranslations('common')
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

  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false)
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false)
  const [datapoolToDelete, setDatapoolToDelete] = useState<string | null>(null)

  const { mutate: deleteDatapool } = useDeleteDatapool()

  const { hasPermissionInScope } = usePermissions()
  const canCreate = hasPermissionInScope(PERMISSION_NAMES.DATAPOOL_CREATE, ASSIGNMENT_SCOPE_TYPES.TENANT)

  const handleDeleteClick = (datapool: DatapoolSummary) => {
    setDatapoolToDelete(datapool.id)
    if ((datapool?.datasets?.length ?? 0) > 0) setIsInfoModalOpen(true)
    else setIsDeleteModalOpen(true)
  }

  const handleConfirmDelete = () => {
    if (!datapoolToDelete) return

    deleteDatapool(datapoolToDelete, {
      onSuccess: () => {
        setDatapoolToDelete(null)
        setIsDeleteModalOpen(false)
        toast.success(tCommon('messages.deleteSuccess', { item: tCommon('items.datapool') }))
        router.refresh()
      },
      onError: () => {
        setDatapoolToDelete(null)
        setIsDeleteModalOpen(false)
        toast.error(t('errors.deleteError'))
      },
    })
  }

  useEffect(() => setTotalPages(Math.ceil(rowCount / pageSize) || 1), [rowCount, pageSize, setTotalPages])

  const CustomElement = canCreate ? (
    <Button
      data-testid="addDatapoolButton"
      onClick={() => router.push(`datapools/create?${getApiRequestParamsByUrl().toString()}`)}
    >
      <Plus />
      {t('addDatapool')}
    </Button>
  ) : undefined

  return (
    <>
      <PageContainer testId="datapoolsPage" headerType="onlyTitle">
        <PageHeader title={t('title')} />
        <PageBackground>
          <SearchHeader
            searchString={search}
            onChangeSearchString={setSearchParam}
            aria-label={t('searchDatapools')}
            customElement={CustomElement}
          />
          <TableContainer>
            <DatapoolsTable
              datapools={datapools}
              rowCount={rowCount}
              pageIndex={pageIndex}
              pageSize={pageSize}
              sorting={sorting}
              totalPages={totalPages}
              onPaginationChange={setPaginationParams}
              onSortingChange={setSortingParams}
              onDeleteClick={handleDeleteClick}
            />
          </TableContainer>
        </PageBackground>
      </PageContainer>

      <InfoModal
        title={t('infoModal.title')}
        description={t('infoModal.description')}
        buttonTitle={tCommon('actions.close')}
        onClose={() => setIsInfoModalOpen(false)}
        open={isInfoModalOpen}
      />
      <WarningModal
        title={t('deleteModal.title')}
        description={t('deleteModal.description')}
        confirmButtonTitle={tCommon('actions.delete')}
        onConfirm={() => {
          handleConfirmDelete()
        }}
        onDiscard={() => setIsDeleteModalOpen(false)}
        open={isDeleteModalOpen}
      />
    </>
  )
}

export default DatapoolsList
