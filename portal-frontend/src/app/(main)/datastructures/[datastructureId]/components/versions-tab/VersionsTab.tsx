'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import {
  useDeleteDatastructureVersion,
  useDuplicateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { DatastructureVersionsListData } from '@/types/datastructures'
import { isResourceInUseError } from '@/utils/errors'

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
  const tVersion = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const [versionToDelete, setVersionToDelete] = useState<string | null>(null)
  const [isDeletionInfoModalOpen, setIsDeletionInfoModalOpen] = useState(false)
  const deleteVersion = useDeleteDatastructureVersion()
  const duplicateVersion = useDuplicateDatastructureVersion()
  const createVersionPath = `/datastructures/${datastructureId}/createVersion?mode=edit`
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

  const handleDeleteVersion = () => {
    if (!versionToDelete) return
    deleteVersion.mutate(
      { data: undefined, endpoint: `/datastructures/${datastructureId}/versions/${versionToDelete}` },
      {
        onSuccess: () => {
          toast.success(tCommon('success.deletionSuccess', { item: tCommon('items.datastructureVersion') }))
          router.refresh()
        },
        onError: error => {
          if (isResourceInUseError(error)) setIsDeletionInfoModalOpen(true)
          else toast.error(tVersion('errors.deletionError'))
        },
        onSettled: () => setVersionToDelete(null),
      },
    )
  }

  const handleDeleteVersionClick = (versionId: string) => {
    const version = versions.find(version => version.id === versionId)
    if (!version) return
    if (version.inUse) setIsDeletionInfoModalOpen(true)
    else setVersionToDelete(versionId)
  }

  const handleDuplicateVersionClick = (versionId: string) => {
    duplicateVersion.mutate(
      { data: undefined, endpoint: `/datastructures/${datastructureId}/versions/${versionId}/duplicate` },
      {
        onSuccess: () => {
          toast.success(tVersion('messages.duplicateSuccess'))
          router.refresh()
        },
        onError: () => {
          toast.error(tVersion('errors.duplicationError'))
        },
      },
    )
  }

  const CustomElement = (
    <Button data-testid="addVersionButton" asChild>
      <GuardedLink href={createVersionPath}>
        <Plus />
        {t('newVersion')}
      </GuardedLink>
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
        isReadOnly={isReadOnly}
        isDuplicating={duplicateVersion.isPending}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        sorting={sorting}
        totalPages={totalPages}
        onPaginationChange={setPaginationParams}
        onSortingChange={setSortingParams}
        onDeleteVersionClick={handleDeleteVersionClick}
        onDuplicateVersionClick={handleDuplicateVersionClick}
      />
      <InfoModal
        open={isDeletionInfoModalOpen}
        title={tVersion('infoModal.title')}
        description={tVersion('infoModal.description')}
        onClose={() => setIsDeletionInfoModalOpen(false)}
      />
      <WarningModal
        open={!!versionToDelete}
        title={tVersion('deleteConfirm.title')}
        description={tVersion('deleteConfirm.description')}
        onDiscard={() => setVersionToDelete(null)}
        confirmButtonTitle={tCommon('actions.delete')}
        onConfirm={handleDeleteVersion}
        isLoading={deleteVersion.isPending}
      />
    </TableContainer>
  )
}
