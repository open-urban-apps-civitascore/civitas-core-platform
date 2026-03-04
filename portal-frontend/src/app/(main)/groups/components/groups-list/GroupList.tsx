'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { useDeleteGroup } from '@/app/services/api/groups/clientRequests'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group } from '@/types/groups'

import { GroupsTable } from './GroupsTable'

interface GroupsListProps {
  groupsData: Group[]
  totalCount: number
}

const GroupsList = (props: GroupsListProps) => {
  const { groupsData, totalCount } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [groupToDelete, setGroupToDelete] = useState<string | null>(null)
  const [isWarningModalOpen, setIsWarningmodalOpen] = useState(false)
  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false)

  const deleteGroup = useDeleteGroup()

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

  const rowCount = totalCount || 0
  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  const handleRowClick = (row: Row<Group>) => {
    const params = getApiRequestParamsByUrl()
    router.push(`groups/${row.id}?${params}`)
  }

  const handleDeleteGroup = (groupId: string) => {
    deleteGroup.mutate(groupId, {
      onSuccess: () => {
        toast.success(tCommon('messages.deleteSuccess', { item: tCommon('items.group') }))
        setIsWarningmodalOpen(false)
        router.refresh()
      },
      onError: () => {
        toast.error(t('errors.deleteError'))
        setIsWarningmodalOpen(false)
      },
    })
  }

  const handleConfirmDeletion = () => {
    if (groupToDelete) {
      handleDeleteGroup(groupToDelete)
    }
    setGroupToDelete(null)
  }

  const handleDeleteGroupClick = (groupId: string) => {
    const group = groupsData.find(group => group.id === groupId)
    if (!group) return
    if ((group?.members?.length && group?.members?.length > 0) || (group?.roles?.length && group?.roles?.length > 0))
      setIsInfoModalOpen(true)
    else {
      setGroupToDelete(groupId)
      setIsWarningmodalOpen(true)
    }
  }

  const CustomElement = () => {
    const params = getApiRequestParamsByUrl()
    return (
      <Button onClick={() => router.push(`groups/create?${params}`)}>
        <Plus />
        {t('newGroup')}
      </Button>
    )
  }

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader
          customElement={<CustomElement />}
          onChangeSearchString={setSearchParam}
          searchString={search}
          placeholder={t('search')}
        />
        <TableContainer>
          <GroupsTable
            groups={groupsData}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onRowClick={handleRowClick}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
            onDeleteGroupClick={handleDeleteGroupClick}
          />
        </TableContainer>
      </PageBackground>
      <InfoModal
        open={isInfoModalOpen}
        title={t('infoModal.title')}
        description={t('infoModal.description')}
        onClose={() => setIsInfoModalOpen(false)}
      />
      <WarningModal
        open={isWarningModalOpen}
        title={t('warningModal.title')}
        description={t('warningModal.description')}
        onConfirm={handleConfirmDeletion}
        onDiscard={() => setIsWarningmodalOpen(false)}
        isLoading={deleteGroup.isPending}
        confirmButtonTitle={tCommon('actions.delete')}
      />
    </PageContainer>
  )
}

export default GroupsList
