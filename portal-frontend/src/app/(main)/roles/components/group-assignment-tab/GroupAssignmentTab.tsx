'use client'

import { Row } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { AlertBox, InfoBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { type Assignment, ASSIGNMENT_SCOPE_TYPES, type AssignmentScope } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import { GroupTable, GroupTableRow } from './GroupTable'
import { RemoveGroupAssignmentModal } from './RemoveGroupAssignmentModal'

interface GroupAssignmentTabProps {
  selectedGroupIds: Group['id'][]
  onGroupAssignmentUpdate: (newGroupIds: string[]) => void
  roleName: Role['name']
  isReadOnly: boolean
  initialAssignments: Assignment[]
  isSystemRole: boolean
  getAssignmentsError?: Error | null
}

export const GroupAssignmentTab = (props: GroupAssignmentTabProps) => {
  const {
    selectedGroupIds,
    onGroupAssignmentUpdate,
    roleName,
    isReadOnly,
    initialAssignments,
    isSystemRole,
    getAssignmentsError,
  } = props

  const t = useTranslations('roles.groupAssignmentTab')
  const tCommon = useTranslations('common')

  const scopeTabs: { value: AssignmentScope; label: string }[] = [
    { value: ASSIGNMENT_SCOPE_TYPES.TENANT, label: t('scopeTabs.platformWide') },
    { value: ASSIGNMENT_SCOPE_TYPES.DATAPOOL, label: t('scopeTabs.datapool') },
    { value: ASSIGNMENT_SCOPE_TYPES.DATASET, label: t('scopeTabs.dataset') },
    { value: ASSIGNMENT_SCOPE_TYPES.DATASOURCE, label: t('scopeTabs.datasource') },
    { value: ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE, label: t('scopeTabs.datastructure') },
  ]

  const router = useRouter()
  const { hasPermission } = usePermissions()
  const canCreateAssignment = hasPermission(PERMISSION_NAMES.ASSIGNMENT_CREATE)
  const canDeleteAssignment = hasPermission(PERMISSION_NAMES.ASSIGNMENT_DELETE)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [selectedScope, setSelectedScope] = useState<AssignmentScope>(ASSIGNMENT_SCOPE_TYPES.TENANT)
  const [groupToRemove, setGroupToRemove] = useState<GroupTableRow | null>(null)
  const {
    pageIndex,
    pageSize,
    sorting,
    search,
    totalPages,
    setTotalPages,
    setPaginationParams,
    setSortingParams,
    setSearchParam,
    getApiRequestParams,
  } = useQueryParams()

  const getGroupRequestIds = () => {
    if (selectedScope === ASSIGNMENT_SCOPE_TYPES.TENANT) return selectedGroupIds.join(',')
    else {
      const scopeAssignments = initialAssignments.filter(assignment => assignment.scopeType === selectedScope)
      const scopeAssignmentGroupIds = [...new Set(scopeAssignments.map(assignment => assignment.group.id))]
      return scopeAssignmentGroupIds.join(',')
    }
  }

  const groupRequestIds = getGroupRequestIds()

  const requestParams = useMemo(() => {
    const requestParams = getApiRequestParams({ pageIndex, pageSize, sorting, search })
    if (!!groupRequestIds) requestParams.set('id', groupRequestIds)
    return requestParams
  }, [getApiRequestParams, groupRequestIds, pageIndex, pageSize, sorting, search])

  const { data: groupsData, isFetching } = useGetGroups({
    params: requestParams,
  })

  const rowCount = groupsData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  useEffect(() => {
    setPaginationParams({ pageSize: pageSize ?? 10, pageIndex: 0 })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedScope])

  // Filter assignments by selected scope
  const scopeFilteredAssignments = useMemo(() => {
    if (selectedScope === ASSIGNMENT_SCOPE_TYPES.TENANT) {
      return selectedGroupIds.map(id => ({ groupId: id, scopeType: ASSIGNMENT_SCOPE_TYPES.TENANT as AssignmentScope }))
    }
    return initialAssignments
      .filter(a => a.scopeType === selectedScope)
      .map(a => ({ groupId: a.group.id, scopeType: a.scopeType }))
  }, [initialAssignments, selectedScope, selectedGroupIds])

  const scopeFilteredGroupIds = useMemo(
    () => [...new Set(scopeFilteredAssignments.map(a => a.groupId))],
    [scopeFilteredAssignments],
  )

  const groups = useMemo(() => {
    if (!groupsData?.data) {
      return []
    }
    return groupsData.data
      .filter(group => scopeFilteredGroupIds.includes(group.id))
      .map(group => ({
        ...group,
        scopeType: scopeFilteredAssignments.find(a => a.groupId === group.id)?.scopeType,
      })) as GroupTableRow[]
  }, [groupsData?.data, scopeFilteredGroupIds, scopeFilteredAssignments])

  const onRowClick = (row: Row<GroupTableRow>) => {
    router.push(`/groups/${row.original.id}`)
  }

  const handleRemoveGroup = (group: GroupTableRow) => {
    const newGroupIds = selectedGroupIds.filter(id => id !== group.id)
    onGroupAssignmentUpdate(newGroupIds)
    setGroupToRemove(null)
  }

  const isTenantScope = selectedScope === ASSIGNMENT_SCOPE_TYPES.TENANT
  const canEdit = !isReadOnly && isTenantScope

  const handleAddGroups = (newGroupIds: string[]) => {
    onGroupAssignmentUpdate(Array.from(new Set([...selectedGroupIds, ...newGroupIds])))
  }

  if (isFetching) {
    return <LoadingSpinner />
  }

  const addGroupButton =
    canEdit && canCreateAssignment ? (
      <Button onClick={() => setIsModalOpen(true)}>
        <Plus /> {t('addGroup')}
      </Button>
    ) : null

  if (getAssignmentsError) {
    return <NoDataPage className="h-full" title={tCommon('errors.loadingError')} />
  }

  // No data state
  if (!isFetching && scopeFilteredGroupIds.length === 0 && groups.length === 0) {
    return (
      <div className="flex flex-col gap-4 h-full">
        {!isSystemRole && (
          <div className="flex items-center justify-between gap-4">
            <div className="flex gap-2" role="tablist">
              {scopeTabs.map(tab => (
                <Button
                  key={tab.value}
                  type="button"
                  role="tab"
                  aria-selected={selectedScope === tab.value}
                  variant={selectedScope === tab.value ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setSelectedScope(tab.value)}
                >
                  {tab.label}
                </Button>
              ))}
            </div>
            {!isTenantScope && <AlertBox text={t(`scopeReadOnlyMessage${selectedScope}`)} />}
            {isTenantScope && <InfoBox text={t('infoBox')} />}
          </div>
        )}

        <SearchHeader
          searchString={search}
          onChangeSearchString={setSearchParam}
          customElement={isReadOnly ? undefined : addGroupButton}
        />

        <NoDataPage title={t('noGroupsAssigned')} />

        {isTenantScope && (
          <GroupAssignmentModal
            open={isModalOpen}
            onOpenChange={setIsModalOpen}
            assignedGroupIds={selectedGroupIds}
            onAddGroups={handleAddGroups}
            roleName={roleName}
          />
        )}
      </div>
    )
  }

  return (
    <div data-testid="groupAssignmentTab" className="flex flex-col gap-4">
      {!isSystemRole && (
        <div className="flex items-center justify-between gap-4">
          <div className="flex gap-2" role="tablist">
            {scopeTabs.map(tab => (
              <Button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={selectedScope === tab.value}
                variant={selectedScope === tab.value ? 'default' : 'outline'}
                size="sm"
                onClick={() => setSelectedScope(tab.value)}
              >
                {tab.label}
              </Button>
            ))}
          </div>
          {!isTenantScope && <AlertBox text={t(`scopeReadOnlyMessage${selectedScope}`)} />}
          {isTenantScope && <InfoBox text={t('infoBox')} />}
        </div>
      )}

      <SearchHeader
        searchString={search}
        onChangeSearchString={setSearchParam}
        customElement={isReadOnly ? undefined : addGroupButton}
      />
      <GroupTable
        groups={groups}
        isLoading={isFetching}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        onPaginationChange={setPaginationParams}
        sorting={sorting}
        onSortingChange={setSortingParams}
        totalPages={totalPages}
        onRowClick={canEdit ? undefined : onRowClick}
        isEditMode={canEdit}
        onRemoveGroup={canEdit && canDeleteAssignment ? group => setGroupToRemove(group) : undefined}
      />
      {isTenantScope && (
        <GroupAssignmentModal
          open={isModalOpen}
          onOpenChange={setIsModalOpen}
          assignedGroupIds={selectedGroupIds}
          onAddGroups={handleAddGroups}
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
