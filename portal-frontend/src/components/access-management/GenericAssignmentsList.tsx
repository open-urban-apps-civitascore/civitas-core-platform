'use client'

import { InfoIcon, List, Plus, SquarePen, TriangleAlert } from 'lucide-react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { toast } from 'sonner'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'
import { CreateAssignmentData } from '@/types/assignments'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { mapGroupRoleAssignmentsToCreateData } from '@/utils/assignmentMapper'

import { AccessManagementTable, GroupRoleAssignmentTable } from './AccessManagementTable'
import { AddGroupModal } from './AddGroupModal'
import { AddRoleModal } from './AddRoleModal'

type GenericAssignmentsListProps = {
  entityId: string
  initialAssignments: GroupRoleAssignmentTable[]
  groups: Group[]
  roles: Role[]
  onPatchEntity: (id: string, assignments: CreateAssignmentData[]) => Promise<void>
  title: string
  subtitle: string
  testId?: string
}

export const GenericAssignmentsList = (props: GenericAssignmentsListProps) => {
  const {
    entityId,
    initialAssignments,
    groups,
    roles,
    onPatchEntity,
    title,
    subtitle,
    testId = 'accessManagement',
  } = props
  const params = useSearchParams()
  const mode = params.get('mode')

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')

  const router = useRouter()

  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)
  const [isAddGroupModalOpen, setIsAddGroupModalOpen] = useState(false)
  const [isAddRoleModalOpen, setIsAddRoleModalOpen] = useState(false)
  const [selectedGroupForRole, setSelectedGroupForRole] = useState<string | null>(null)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  // Helper functions to detect changes compared to initial state
  const detectGroupChanges = (): boolean => {
    const currentGroupIds = assignedGroups.map(g => g.groupId).sort()
    const initialGroupIds = initialAssignments.map(g => g.groupId).sort()
    return JSON.stringify(currentGroupIds) !== JSON.stringify(initialGroupIds)
  }

  const detectRoleChanges = (): boolean => {
    // Check if roles within existing groups have changed
    for (const currentGroup of assignedGroups) {
      const initialGroup = initialAssignments.find(g => g.groupId === currentGroup.groupId)
      if (!initialGroup) continue // New group, doesn't count as role change

      const currentRoleIds = currentGroup.assignedRoles.map(r => r.roleId).sort()
      const initialRoleIds = initialGroup.assignedRoles.map(r => r.roleId).sort()

      if (JSON.stringify(currentRoleIds) !== JSON.stringify(initialRoleIds)) {
        return true
      }
    }
    return false
  }

  const hasGroupChanges = detectGroupChanges()
  const hasRoleChanges = detectRoleChanges()

  // Check if there are any groups without roles
  const hasGroupsWithoutRoles = assignedGroups.some(group => group.assignedRoles.length === 0)

  const handleDeleteGroup = (groupId: string) => {
    setAssignedGroups(prev => prev.filter(assignment => assignment.groupId !== groupId))
  }

  const handleAddAssignmentClick = () => {
    setIsAddGroupModalOpen(true)
  }

  const handleAddGroups = (groupIds: string[]) => {
    const groupsToAdd = groups.filter(group => groupIds.includes(group.id))

    if (groupsToAdd.length === 0) return

    const newAssignments: GroupRoleAssignmentTable[] = groupsToAdd.map(groupDetails => ({
      groupId: groupDetails.id,
      groupName: groupDetails.name,
      groupDescription: groupDetails.description,
      assignedRoles: [], // initially empty, roles can be added later
    }))

    setAssignedGroups(prev => [...prev, ...newAssignments])
  }

  const handleAddRoleClick = (groupId: string) => {
    setSelectedGroupForRole(groupId)
    setIsAddRoleModalOpen(true)
  }

  const handleAddRoles = (roleIds: string[]) => {
    if (!selectedGroupForRole) return

    const rolesToAdd = roles.filter(role => roleIds.includes(role.id))
    if (rolesToAdd.length === 0) return

    setAssignedGroups(prev =>
      prev.map(group =>
        group.groupId === selectedGroupForRole
          ? {
              ...group,
              assignedRoles: [
                ...group.assignedRoles,
                ...rolesToAdd.map(role => ({
                  roleId: role.id,
                  roleName: role.name,
                })),
              ],
            }
          : group,
      ),
    )
  }

  const handleDeleteRole = (groupId: string, roleId: string) => {
    setAssignedGroups(prev =>
      prev.map(group =>
        group.groupId === groupId
          ? {
              ...group,
              assignedRoles: group.assignedRoles.filter(role => role.roleId !== roleId),
            }
          : group,
      ),
    )
  }

  const assignedGroupIds = assignedGroups
    .map(assignment => {
      return groups.find(group => group.id === assignment.groupId)?.id || ''
    })
    .filter(id => id !== '')

  const onSubmit = async () => {
    const dataToSubmit = mapGroupRoleAssignmentsToCreateData(assignedGroups)

    setIsLoading(true)
    try {
      await onPatchEntity(entityId, dataToSubmit)
      toast.success(t('messages.updateSuccess'))
      setIsExitModalOpen(false)
      setIsReadOnly(true)
      router.refresh()
    } catch (error) {
      console.error('Error updating entity:', error)
      toast.error(tCommon('errors.unexpectedError'))
      setAssignedGroups(initialAssignments)
      setIsReadOnly(true)
    } finally {
      setIsLoading(false)
    }
  }

  const handleCancel = () => {
    const hasChanges = hasGroupChanges || hasRoleChanges
    if (hasChanges && !hasGroupsWithoutRoles) {
      setIsExitModalOpen(true)
    } else {
      setIsReadOnly(true)
      setAssignedGroups(initialAssignments)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    setAssignedGroups(initialAssignments)
    setIsReadOnly(true)
  }

  const EditModeButtons = (
    <ActionButtons
      onCancelClick={handleCancel}
      onConfirmClick={onSubmit}
      confirmButtonType="button"
      confirmButtonTitle={tCommon('actions.submit')}
      cancelButtonTitle={tCommon('actions.exit')}
      isCancelButtonDisabled={false}
      isConfirmButtonDisabled={(!hasGroupChanges && !hasRoleChanges) || hasGroupsWithoutRoles}
      hasCard={false}
    />
  )

  const EditButton = (
    <Button variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader title={title} subtitle={subtitle} customElement={isReadOnly ? EditButton : EditModeButtons} />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <div className="min-h-0">
          {assignedGroups.length === 0 ? (
            <ContentCard className="flex flex-col items-center gap-4 py-16">
              <List className="mb-0.5 mx-auto border-2 border-gray-300 border-solid p-1 rounded-1.5" size={40} />
              <NoDataPage
                title={t('noDataPage.title')}
                subTitle={t('noDataPage.description')}
                buttonText={t('addAssignment')}
                onButtonClick={handleAddAssignmentClick}
                isDisabled={isReadOnly}
              />
            </ContentCard>
          ) : (
            <>
              {!isReadOnly && (
                <div className="flex flex-col items-end mb-4">
                  <Button onClick={handleAddAssignmentClick} disabled={isReadOnly}>
                    <Plus className="h-4 w-4 mr-2" />
                    {t('addAssignment')}
                  </Button>
                </div>
              )}

              <AccessManagementTable
                assignments={assignedGroups}
                onDeleteClick={id => handleDeleteGroup(id as string)}
                onAddRoleClick={handleAddRoleClick}
                onDeleteRole={handleDeleteRole}
                isReadOnly={isReadOnly}
                isLoading={isLoading}
              />

              <div className="mt-6 space-y-4">
                <ContentCard className="flex flex-row items-start justify-start gap-3">
                  <InfoIcon className="h-5 w-5 text-muted-foreground shrink-0 mt-0.5" />
                  <p className="text-sm text-muted-foreground">{t('infoBoxes.firstBox')}</p>
                </ContentCard>

                {!isReadOnly && (
                  <ContentCard className="flex flex-row items-start justify-start gap-3">
                    <TriangleAlert className="h-5 w-5 text-muted-foreground shrink-0 mt-0.5" />
                    <p className="text-sm text-muted-foreground">{t('infoBoxes.secondBox')}</p>
                  </ContentCard>
                )}
              </div>
            </>
          )}
        </div>
      </PageBackground>
      <AddGroupModal
        open={isAddGroupModalOpen}
        onOpenChange={setIsAddGroupModalOpen}
        groups={groups}
        assignedGroupIds={assignedGroupIds}
        onAddGroups={handleAddGroups}
      />
      <AddRoleModal
        open={isAddRoleModalOpen}
        onOpenChange={setIsAddRoleModalOpen}
        roles={roles}
        assignedRoleIds={
          selectedGroupForRole
            ? assignedGroups
                .find(group => group.groupId === selectedGroupForRole)
                ?.assignedRoles.map(role => role.roleId) || []
            : []
        }
        onAddRoles={handleAddRoles}
        groupName={
          selectedGroupForRole
            ? assignedGroups.find(group => group.groupId === selectedGroupForRole)?.groupName || ''
            : ''
        }
      />
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={onSubmit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
