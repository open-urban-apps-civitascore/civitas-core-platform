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
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { PERMISSION_NAMES } from '@/types/currentUser'
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
  const [isDeleteGroupWarningModalOpen, setIsDeleteGroupWarningModalOpen] = useState(false)
  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false)

  const deleteGroup = useDeleteGroup()
  const { hasPermission } = usePermissions()

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
        setIsDeleteGroupWarningModalOpen(false)
        router.refresh()
      },
      onError: () => {
        toast.error(t('errors.deleteError'))
        setIsDeleteGroupWarningModalOpen(false)
      },
      onSettled: () => setGroupToDelete(null),
    })
  }

  const handleConfirmDeletion = () => {
    if (groupToDelete) {
      handleDeleteGroup(groupToDelete)
    }
  }

  const handleDeleteGroupClick = (groupId: string) => {
    const group = groupsData.find(group => group.id === groupId)
    if (!group) return
    if (
      (group?.members?.length && group?.members?.length > 0) ||
      (group?.assignments?.length && group?.assignments?.length > 0)
    )
      setIsInfoModalOpen(true)
    else {
      setGroupToDelete(groupId)
      setIsDeleteGroupWarningModalOpen(true)
    }
  }

  const CustomElement = hasPermission(PERMISSION_NAMES.GROUP_CREATE) ? (
    <Button onClick={() => router.push(`groups/create?${getApiRequestParamsByUrl()}`)}>
      <Plus />
      {t('newGroup')}
    </Button>
  ) : undefined

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader
          customElement={CustomElement}
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
            onDeleteGroupClick={hasPermission(PERMISSION_NAMES.GROUP_DELETE) ? handleDeleteGroupClick : undefined}
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
        open={isDeleteGroupWarningModalOpen}
        title={t('warningModal.title')}
        description={t('warningModal.description')}
        onConfirm={handleConfirmDeletion}
        onDiscard={() => setIsDeleteGroupWarningModalOpen(false)}
        isLoading={deleteGroup.isPending}
        confirmButtonTitle={tCommon('actions.delete')}
      />
    </PageContainer>
  )
}

export default GroupsList
