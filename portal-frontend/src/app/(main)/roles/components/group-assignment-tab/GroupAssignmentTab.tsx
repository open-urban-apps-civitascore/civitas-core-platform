'use client'

import { PaginationState, Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus, TriangleAlert } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { AssignmentScopeType, AssignmentSummary } from '@/app/services/api/assignments/clientRequests'
import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { SegmentedControlBar, Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import { GroupTable, GroupTableRow } from './GroupTable'
import { RemoveGroupAssignmentModal } from './RemoveGroupAssignmentModal'

interface GroupAssignmentTabProps {
  assignedGroupIds: Group['id'][]
  onGroupAssignmentUpdate: (newGroupIds: string[]) => void
  roleName: Role['name']
  isEditMode: boolean
  assignments: AssignmentSummary[]
}

const SCOPE_TABS: Tab<AssignmentScopeType>[] = [
  { label: 'roles.groupAssignmentTab.scopeTabs.platformWide', value: 'TENANT' },
  { label: 'roles.groupAssignmentTab.scopeTabs.dataset', value: 'DATASET' },
  { label: 'roles.groupAssignmentTab.scopeTabs.datasource', value: 'DATASOURCE' },
  { label: 'roles.groupAssignmentTab.scopeTabs.datastructure', value: 'DATASTRUCTURE' },
]

const getGroupSelection = (groupIds: Group['id'][]) =>
  groupIds.reduce((acc, groupId) => ({ ...acc, [groupId]: true }), {})

export const GroupAssignmentTab = (props: GroupAssignmentTabProps) => {
  const { assignedGroupIds, onGroupAssignmentUpdate, roleName, isEditMode, assignments } = props
  const t = useTranslations('roles.groupAssignmentTab')
  const router = useRouter()
  const [searchInput, setSearchInput] = useState<string>('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'title', desc: false }])
  const [totalPages, setTotalPages] = useState<number>(1)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [groupSelection, setGroupSelection] = useState<RowSelectionState>(getGroupSelection(assignedGroupIds))
  const originalGroupSelection = getGroupSelection(assignedGroupIds)
  const [selectedScope, setSelectedScope] = useState<AssignmentScopeType>('TENANT')
  const [groupToRemove, setGroupToRemove] = useState<GroupTableRow | null>(null)

  useEffect(() => {
    setGroupSelection(getGroupSelection(assignedGroupIds))
  }, [assignedGroupIds])

  const requestParams = new URLSearchParams(`size=${pageSize}&page=${pageIndex}`)
  const { data: groupsData, isFetching } = useGetGroups({ params: requestParams })

  // Build a map from group ID to scopeType from assignments
  const groupScopeMap = useMemo(() => {
    const map: Record<string, AssignmentScopeType> = {}
    assignments.forEach(a => {
      map[a.group.id] = a.scopeType
    })
    return map
  }, [assignments])

  // Filter assignments by selected scope
  const scopeFilteredGroupIds = useMemo(() => {
    return assignments.filter(a => a.scopeType === selectedScope).map(a => a.group.id)
  }, [assignments, selectedScope])

  const groups = useMemo(() => {
    if (!groupsData?.data) {
      return []
    }
    const filteredGroups = groupsData.data.filter(group => scopeFilteredGroupIds.includes(group.id))
    return filteredGroups.map(group => ({
      ...group,
      scopeType: groupScopeMap[group.id],
    })) as GroupTableRow[]
  }, [groupsData?.data, scopeFilteredGroupIds, groupScopeMap])

  const filteredGroups = useMemo(() => {
    if (!searchInput) {
      return groups
    }
    return groups.filter(
      group =>
        group.name.toLowerCase().includes(searchInput.toLowerCase()) ||
        group.contactUser?.name.toLowerCase().includes(searchInput.toLowerCase()) ||
        group.description.toLowerCase().includes(searchInput.toLowerCase()),
    )
  }, [searchInput, groups])

  const rowCount = groups.length

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  // Reset pagination when scope changes
  useEffect(() => {
    setPageIndex(0)
  }, [selectedScope])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const onRowClick = (row: Row<GroupTableRow>) => {
    router.push(`/groups/${row.original.id}`)
  }

  const handleRemoveGroup = (group: GroupTableRow) => {
    const newGroupIds = assignedGroupIds.filter(id => id !== group.id)
    onGroupAssignmentUpdate(newGroupIds)
    setGroupToRemove(null)
  }

  const isTenantScope = selectedScope === 'TENANT'
  const canEdit = isEditMode && isTenantScope

  const customElement = canEdit ? (
    <Button onClick={() => setIsModalOpen(true)}>
      <Plus /> {t('addGroup')}
    </Button>
  ) : null

  const haveGroupsBeenTouched =
    Object.keys(groupSelection).every(key => assignedGroupIds.includes(key)) === false ||
    assignedGroupIds.every(id => Object.keys(groupSelection).includes(id)) === false

  if (isFetching) {
    return <LoadingSpinner />
  }

  // No data state
  if (!isFetching && scopeFilteredGroupIds.length === 0 && filteredGroups.length === 0) {
    return (
      <div className="flex flex-col gap-4 h-full">
        <div className="flex items-center justify-between gap-4">
          <SegmentedControlBar tabs={SCOPE_TABS} selectedTab={selectedScope} onTabChange={setSelectedScope} />

          {!isTenantScope ? (
            <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
              <TriangleAlert className="h-4 w-4 shrink-0" />
              <span>{t(`scopeReadOnlyMessage${selectedScope}`)}</span>
            </div>
          ) : canEdit ? (
            <Button onClick={() => setIsModalOpen(true)}>
              <Plus /> {t('addGroup')}
            </Button>
          ) : null}
        </div>

        <NoDataPage title={t('noGroupsAssigned')} />

        {canEdit && (
          <GroupAssignmentModal
            open={isModalOpen}
            onOpenChange={setIsModalOpen}
            selection={groupSelection}
            setSelection={setGroupSelection}
            originalSelection={originalGroupSelection}
            onGroupAssignmentUpdate={onGroupAssignmentUpdate}
            haveGroupsBeenTouched={haveGroupsBeenTouched}
            roleName={roleName}
          />
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <SegmentedControlBar tabs={SCOPE_TABS} selectedTab={selectedScope} onTabChange={setSelectedScope} />

        {!isTenantScope ? (
          <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
            <TriangleAlert className="h-4 w-4 shrink-0" />
            <span>{t(`scopeReadOnlyMessage${selectedScope}`)}</span>
          </div>
        ) : canEdit ? (
          <Button onClick={() => setIsModalOpen(true)}>
            <Plus /> {t('addGroup')}
          </Button>
        ) : null}
      </div>

      <SearchHeader searchString={searchInput} onChangeSearchString={setSearchInput} />
      <GroupTable
        groups={filteredGroups}
        isLoading={isFetching}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        onPaginationChange={handlePagination}
        sorting={sorting}
        onSortingChange={setSorting}
        totalPages={totalPages}
        onRowClick={onRowClick}
        isEditMode={canEdit}
        onRemoveGroup={canEdit ? group => setGroupToRemove(group) : undefined}
      />

      {canEdit && (
        <GroupAssignmentModal
          open={isModalOpen}
          onOpenChange={setIsModalOpen}
          selection={groupSelection}
          setSelection={setGroupSelection}
          originalSelection={originalGroupSelection}
          onGroupAssignmentUpdate={onGroupAssignmentUpdate}
          haveGroupsBeenTouched={haveGroupsBeenTouched}
          roleName={roleName}
        />
      )}

      {groupToRemove && (
        <RemoveGroupAssignmentModal
          open={!!groupToRemove}
          onOpenChange={open => {
            if (!open) setGroupToRemove(null)
          }}
          groupName={groupToRemove.name}
          onConfirm={() => handleRemoveGroup(groupToRemove)}
        />
      )}
    </div>
  )
}
