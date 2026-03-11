'use client'

import { PaginationState, Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Info, Plus, TriangleAlert } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { AssignmentSummary } from '@/app/services/api/assignments/clientRequests'
import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { SegmentedControlBar, Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { ASSIGNMENT_SCOPE_TYPES, AssignmentScope } from '@/types/assignments'
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

const SCOPE_TABS: Tab<AssignmentScope>[] = [
  { label: 'roles.groupAssignmentTab.scopeTabs.platformWide', value: ASSIGNMENT_SCOPE_TYPES.TENANT },
  { label: 'roles.groupAssignmentTab.scopeTabs.dataset', value: ASSIGNMENT_SCOPE_TYPES.DATASET },
  { label: 'roles.groupAssignmentTab.scopeTabs.datasource', value: ASSIGNMENT_SCOPE_TYPES.DATASOURCE },
  { label: 'roles.groupAssignmentTab.scopeTabs.datastructure', value: ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE },
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
  const [selectedScope, setSelectedScope] = useState<AssignmentScope>(ASSIGNMENT_SCOPE_TYPES.TENANT)
  const [groupToRemove, setGroupToRemove] = useState<GroupTableRow | null>(null)

  useEffect(() => {
    setGroupSelection(getGroupSelection(assignedGroupIds))
  }, [assignedGroupIds])

  const requestParams = new URLSearchParams(`size=${pageSize}&page=${pageIndex}`)
  const { data: groupsData, isFetching } = useGetGroups({ params: requestParams })

  // Build a map from group ID to scopeType from assignments
  const groupScopeMap = useMemo(() => {
    const map: Record<string, AssignmentScope | null> = {}
    assignments.forEach(a => {
      map[a.group.id] = a.scopeType
    })
    return map
  }, [assignments])

  // Filter assignments by selected scope
  const scopeFilteredGroupIds = useMemo(() => {
    return assignments
      .filter(
        a => a.scopeType === selectedScope || (selectedScope === ASSIGNMENT_SCOPE_TYPES.TENANT && a.scopeType == null),
      )
      .map(a => a.group.id)
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

  const isTenantScope = selectedScope === ASSIGNMENT_SCOPE_TYPES.TENANT
  const canEdit = isEditMode && isTenantScope

  const haveGroupsBeenTouched =
    Object.keys(groupSelection).every(key => assignedGroupIds.includes(key)) === false ||
    assignedGroupIds.every(id => Object.keys(groupSelection).includes(id)) === false

  if (isFetching) {
    return <LoadingSpinner />
  }

  const addGroupButton = isTenantScope ? (
    <Button onClick={() => setIsModalOpen(true)}>
      <Plus /> {t('addGroup')}
    </Button>
  ) : null

  // No data state
  if (!isFetching && scopeFilteredGroupIds.length === 0 && filteredGroups.length === 0) {
    return (
      <div className="flex flex-col gap-4 h-full">
        <div className="flex items-center justify-between gap-4">
          <SegmentedControlBar tabs={SCOPE_TABS} selectedTab={selectedScope} onTabChange={setSelectedScope} />

          {!isTenantScope && (
            <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
              <TriangleAlert className="h-4 w-4 shrink-0" />
              <span>{t(`scopeReadOnlyMessage${selectedScope}`)}</span>
            </div>
          )}

          {isTenantScope && (
            <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
              <Info className="h-4 w-4 shrink-0" />
              <span>{t('infoBox')}</span>
            </div>
          )}
        </div>

        <SearchHeader searchString={searchInput} onChangeSearchString={setSearchInput} customElement={addGroupButton} />

        <NoDataPage title={t('noGroupsAssigned')} />

        {isTenantScope && (
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

        {!isTenantScope && (
          <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
            <TriangleAlert className="h-4 w-4 shrink-0" />
            <span>{t(`scopeReadOnlyMessage${selectedScope}`)}</span>
          </div>
        )}
        {isTenantScope && (
          <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
            <Info className="h-4 w-4 shrink-0" />
            <span>{t('infoBox')}</span>
          </div>
        )}
      </div>

      <SearchHeader searchString={searchInput} onChangeSearchString={setSearchInput} customElement={addGroupButton} />
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
        onRowClick={canEdit ? undefined : onRowClick}
        isEditMode={canEdit}
        onRemoveGroup={canEdit ? group => setGroupToRemove(group) : undefined}
      />

      {isTenantScope && (
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
          isOpen={!!groupToRemove}
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
