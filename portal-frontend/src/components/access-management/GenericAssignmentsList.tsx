'use client'

import { InfoIcon, List, Plus, TriangleAlert } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { NoDataCard } from '@/components/no-data/no-data-card/NoDataCard'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { AccessManagementTable, GroupRoleAssignmentTable } from './AccessManagementTable'
import { AddGroupModal } from './AddGroupModal'
import { AddRoleModal } from './AddRoleModal'

/**
 * Renders the group/role assignments of an entity. The parent owns the assignments state,
 * the read-only mode and — where applicable — saving; this component only edits the list.
 */
type GenericAssignmentsListProps = {
  assignedGroups: GroupRoleAssignmentTable[]
  onAssignedGroupsChange: (groups: GroupRoleAssignmentTable[]) => void
  isReadOnly?: boolean
  isLoading?: boolean
  testId?: string
  firstBoxText?: string
  secondBoxText?: string
  hasSecondBox?: boolean
  tableTitle?: string
  tableSubtitle?: string
  noDataSubtitle?: string
}

export const GenericAssignmentsList = (props: GenericAssignmentsListProps) => {
  const {
    assignedGroups,
    onAssignedGroupsChange,
    isReadOnly = false,
    isLoading = false,
    testId = 'accessManagement',
    hasSecondBox = false,
    firstBoxText,
    secondBoxText,
    tableTitle,
    tableSubtitle,
    noDataSubtitle,
  } = props

  const { hasPermission } = usePermissions()
  const canAddGroups = hasPermission(PERMISSION_NAMES.GROUP_READ) && hasPermission(PERMISSION_NAMES.ROLE_READ)
  const canAddRoles = hasPermission(PERMISSION_NAMES.ROLE_READ)

  const t = useTranslations('accessManagement')

  const [isAddGroupModalOpen, setIsAddGroupModalOpen] = useState(false)
  const [isAddRoleModalOpen, setIsAddRoleModalOpen] = useState(false)
  const [selectedGroupForRole, setSelectedGroupForRole] = useState<string | null>(null)

  const handleDeleteGroup = (groupId: string) => {
    onAssignedGroupsChange(assignedGroups.filter(a => a.groupId !== groupId))
  }

  const handleAddAssignmentClick = () => setIsAddGroupModalOpen(true)

  const handleAddGroups = (groups: Group[]) => {
    if (groups.length === 0) return
    const newAssignments: GroupRoleAssignmentTable[] = groups.map(g => ({
      groupId: g.id,
      groupName: g.name,
      groupDescription: g.description,
      assignedRoles: [],
    }))
    onAssignedGroupsChange([...assignedGroups, ...newAssignments])
  }

  const handleAddRoleClick = (groupId: string) => {
    setSelectedGroupForRole(groupId)
    setIsAddRoleModalOpen(true)
  }

  const handleAddRoles = (roles: Role[]) => {
    if (!selectedGroupForRole) return
    if (roles.length === 0) return
    onAssignedGroupsChange(
      assignedGroups.map(group =>
        group.groupId === selectedGroupForRole
          ? {
              ...group,
              assignedRoles: [...group.assignedRoles, ...roles.map(role => ({ roleId: role.id, roleName: role.name }))],
            }
          : group,
      ),
    )
  }

  const handleDeleteRole = (groupId: string, roleId: string) => {
    onAssignedGroupsChange(
      assignedGroups.map(group =>
        group.groupId === groupId
          ? { ...group, assignedRoles: group.assignedRoles.filter(r => r.roleId !== roleId) }
          : group,
      ),
    )
  }

  const assignedGroupIds = assignedGroups.map(a => a.groupId)

  const shouldShowAddButton = !isReadOnly && canAddGroups

  const addButton = shouldShowAddButton ? (
    <Button onClick={handleAddAssignmentClick}>
      <Plus className="h-4 w-4 mr-2" />
      {t('addAssignment')}
    </Button>
  ) : null

  const renderNoData = () => (
    <>
      {addButton && <div className="flex justify-end mb-4">{addButton}</div>}
      <NoDataCard
        icon={<List size={24} />}
        title={t('noDataPage.title')}
        subTitle={noDataSubtitle ?? t('noDataPage.description')}
        isDisabled={isReadOnly}
      />
    </>
  )

  const renderTable = () => (
    <AccessManagementTable
      assignments={assignedGroups}
      onDeleteClick={id => handleDeleteGroup(id as string)}
      onAddRoleClick={canAddRoles ? handleAddRoleClick : undefined}
      onDeleteRole={handleDeleteRole}
      isReadOnly={isReadOnly}
      isLoading={isLoading}
      tableTitle={tableTitle}
      tableSubtitle={tableSubtitle}
      tableAction={addButton}
    />
  )

  const renderInfoBoxes = () => (
    <div className="mt-6 space-y-4">
      <ContentCard className="flex flex-row items-start justify-start gap-3 py-3 px-4">
        <InfoIcon className="h-5 w-5 text-foreground shrink-0 mt-0.5" />
        <p className="text-sm text-foreground">{firstBoxText ?? t('infoBoxes.firstBox')}</p>
      </ContentCard>
      {!isReadOnly && hasSecondBox && (
        <ContentCard className="flex flex-row items-start justify-start gap-3 py-3 px-4">
          <TriangleAlert className="h-5 w-5 text-foreground shrink-0 mt-0.5" />
          <p className="text-sm text-foreground">{secondBoxText ?? t('infoBoxes.secondBox')}</p>
        </ContentCard>
      )}
    </div>
  )

  return (
    <div data-testid={testId}>
      <div className="min-h-0">
        {assignedGroups.length === 0 ? (
          renderNoData()
        ) : (
          <>
            {renderTable()}
            {renderInfoBoxes()}
          </>
        )}
      </div>
      <AddGroupModal
        open={isAddGroupModalOpen}
        onOpenChange={setIsAddGroupModalOpen}
        assignedGroupIds={assignedGroupIds}
        onAddGroups={handleAddGroups}
      />
      <AddRoleModal
        open={isAddRoleModalOpen}
        onOpenChange={setIsAddRoleModalOpen}
        assignedRoleIds={
          selectedGroupForRole
            ? (assignedGroups.find(g => g.groupId === selectedGroupForRole)?.assignedRoles.map(r => r.roleId) ?? [])
            : []
        }
        onAddRoles={handleAddRoles}
        groupName={
          selectedGroupForRole ? (assignedGroups.find(g => g.groupId === selectedGroupForRole)?.groupName ?? '') : ''
        }
      />
    </div>
  )
}
