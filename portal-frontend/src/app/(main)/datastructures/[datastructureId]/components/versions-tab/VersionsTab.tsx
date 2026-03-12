'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { ExitWarningModal as UnsavedChangesModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
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
  isDirty: boolean
  isLoading: boolean
  onSave: () => Promise<boolean>
}

export const VersionsTab = (props: VersionsTabProps) => {
  const { datastructureId, versions, rowCount, isReadOnly, isDirty, isLoading, onSave } = props
  const t = useTranslations('datastructures')
  const router = useRouter()
  const createVersionPath = `/datastructures/${datastructureId}/createVersion?mode=edit`
  const [pathToNavigate, setPathToNavigate] = useState<string>(createVersionPath)
  const [isUnsavedChangesModalOpen, setIsUnsavedChangesModalOpen] = useState(false)
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

  const handleNavigate = (path: string) => {
    if (isDirty) {
      setPathToNavigate(path)
      setIsUnsavedChangesModalOpen(true)
    } else {
      router.push(path)
    }
  }

  const handleDiscardAndNavigate = () => {
    if (isUnsavedChangesModalOpen) {
      router.push(pathToNavigate)
    }
    setIsUnsavedChangesModalOpen(false)
  }

  const handleSaveAndNavigate = async () => {
    const isSaved = await onSave()
    if (isSaved && isUnsavedChangesModalOpen) {
      router.push(pathToNavigate)
    }
  }

  const CustomElement = (
    <Button data-testid="addVersionButton" onClick={() => handleNavigate(createVersionPath)}>
      <Plus />
      {t('newVersion')}
    </Button>
  )

  return (
    <>
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
          onVersionClick={versionId => handleNavigate(`/datastructures/${datastructureId}/${versionId}`)}
        />
      </TableContainer>

      <UnsavedChangesModal
        title={t('unsavedChangesModal.title')}
        description={t('unsavedChangesModal.description')}
        open={!!isUnsavedChangesModalOpen}
        onOpenChange={() => setIsUnsavedChangesModalOpen(false)}
        onDiscard={handleDiscardAndNavigate}
        onConfirm={handleSaveAndNavigate}
        isLoading={isLoading}
      />
    </>
  )
}
