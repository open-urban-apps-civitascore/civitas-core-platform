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
import { AssignmentScopedInput } from '@/types/assignments'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'

import { AccessManagementTable, GroupRoleAssignmentTable } from './AccessManagementTable'
import { AddGroupModal } from './AddGroupModal'
import { AddRoleModal } from './AddRoleModal'

/**
 * Uncontrolled mode: component manages its own state.
 * Used for standalone pages (e.g. datasets).
 * Renders with title, subtitle and save/cancel action buttons.
 */
type UncontrolledProps = {
  entityId: string
  initialAssignments: GroupRoleAssignmentTable[]
  onPatchEntity: (id: string, assignments: AssignmentScopedInput[]) => Promise<void>
  title: string
  subtitle: string
  // controlled props must not be passed
  assignedGroups?: never
  onAssignedGroupsChange?: never
  isReadOnly?: never
}

/**
 * Controlled mode: parent manages the assignments state.
 * Used for embedded contexts (e.g. datasources tab).
 * No title, subtitle or action buttons – the parent handles saving.
 */
type ControlledProps = {
  assignedGroups: GroupRoleAssignmentTable[]
  onAssignedGroupsChange: (groups: GroupRoleAssignmentTable[]) => void
  isReadOnly?: boolean
  // uncontrolled props must not be passed
  entityId?: never
  initialAssignments?: never
  onPatchEntity?: never
  title?: never
  subtitle?: never
}

type GenericAssignmentsListProps = {
  groups: Group[]
  roles: Role[]
  testId?: string
  firstBoxText?: string
  secondBoxText?: string
  hasSecondBox?: boolean
} & (UncontrolledProps | ControlledProps)

export const GenericAssignmentsList = (props: GenericAssignmentsListProps) => {
  const { groups, roles, testId = 'accessManagement', hasSecondBox = false, firstBoxText, secondBoxText } = props

  const isControlled = props.assignedGroups !== undefined

  const params = useSearchParams()
  const mode = params.get('mode')

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')
  const router = useRouter()

  // Internal state — only used in uncontrolled mode
  const [assignedGroupsInternal, setAssignedGroupsInternal] = useState<GroupRoleAssignmentTable[]>(
    props.initialAssignments ?? [],
  )
  const [isReadOnlyInternal, setIsReadOnlyInternal] = useState(mode !== 'edit')
  const [isAddGroupModalOpen, setIsAddGroupModalOpen] = useState(false)
  const [isAddRoleModalOpen, setIsAddRoleModalOpen] = useState(false)
  const [selectedGroupForRole, setSelectedGroupForRole] = useState<string | null>(null)
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  // Resolved values based on mode
  const assignedGroups = isControlled ? (props.assignedGroups as GroupRoleAssignmentTable[]) : assignedGroupsInternal
  const isReadOnly = isControlled ? (props.isReadOnly ?? false) : isReadOnlyInternal
  const initialAssignments = props.initialAssignments ?? []

  // Change detection only used in uncontrolled mode
  const hasChanges = hasAssignmentChanges(assignedGroups, initialAssignments)

  const updateAssignedGroups = (updated: GroupRoleAssignmentTable[]) => {
    if (isControlled) {
      props.onAssignedGroupsChange!(updated)
    } else {
      setAssignedGroupsInternal(updated)
    }
  }

  const handleDeleteGroup = (groupId: string) => {
    updateAssignedGroups(assignedGroups.filter(a => a.groupId !== groupId))
  }

  const handleAddAssignmentClick = () => setIsAddGroupModalOpen(true)

  const handleAddGroups = (groupIds: string[]) => {
    const groupsToAdd = groups.filter(group => groupIds.includes(group.id))
    if (groupsToAdd.length === 0) return

    const newAssignments: GroupRoleAssignmentTable[] = groupsToAdd.map(g => ({
      groupId: g.id,
      groupName: g.name,
      groupDescription: g.description,
      assignedRoles: [],
    }))
    updateAssignedGroups([...assignedGroups, ...newAssignments])
  }

  const handleAddRoleClick = (groupId: string) => {
    setSelectedGroupForRole(groupId)
    setIsAddRoleModalOpen(true)
  }

  const handleAddRoles = (roleIds: string[]) => {
    if (!selectedGroupForRole) return
    const rolesToAdd = roles.filter(role => roleIds.includes(role.id))
    if (rolesToAdd.length === 0) return

    updateAssignedGroups(
      assignedGroups.map(group =>
        group.groupId === selectedGroupForRole
          ? {
              ...group,
              assignedRoles: [
                ...group.assignedRoles,
                ...rolesToAdd.map(role => ({ roleId: role.id, roleName: role.name })),
              ],
            }
          : group,
      ),
    )
  }

  const handleDeleteRole = (groupId: string, roleId: string) => {
    updateAssignedGroups(
      assignedGroups.map(group =>
        group.groupId === groupId
          ? { ...group, assignedRoles: group.assignedRoles.filter(r => r.roleId !== roleId) }
          : group,
      ),
    )
  }

  const assignedGroupIds = assignedGroups
    .map(a => groups.find(g => g.id === a.groupId)?.id ?? '')
    .filter(id => id !== '')

  // Submit / Cancel / Exit handlers only relevant in uncontrolled mode
  const onSubmit = async () => {
    setIsLoading(true)
    const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
    try {
      if (props.onPatchEntity && props.entityId) {
        await props.onPatchEntity(props.entityId, mapGroupRoleAssignmentsToApiPayload(assignedGroups))
      }
      toast.success(t('messages.updateSuccess'))
      if (areAssignmentsInvalid) {
        toast.error(t('messages.groupsWithoutRoles'))
      }
      setIsExitModalOpen(false)
      setIsReadOnlyInternal(true)
      setAssignedGroupsInternal(prev => prev.filter(g => g.assignedRoles.length > 0))
      router.refresh()
    } catch (error) {
      console.error('Error updating entity:', error)
      toast.error(tCommon('errors.unexpectedError'))
      setAssignedGroupsInternal(initialAssignments)
      setIsReadOnlyInternal(true)
    } finally {
      setIsLoading(false)
    }
  }

  const handleCancel = () => {
    if (hasChanges) {
      setIsExitModalOpen(true)
    } else {
      setIsReadOnlyInternal(true)
      setAssignedGroupsInternal(initialAssignments)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    setAssignedGroupsInternal(initialAssignments)
    setIsReadOnlyInternal(true)
  }

  // Shared content
  const mainContent = (
    <div className="min-h-0">
      {!isReadOnly && (
        <div className="flex justify-end mb-4">
          <Button onClick={handleAddAssignmentClick}>
            <Plus className="h-4 w-4 mr-2" />
            {t('addAssignment')}
          </Button>
        </div>
      )}
      {assignedGroups.length === 0 ? (
        <ContentCard className="w-full">
          <div className="flex flex-col items-center gap-6 p-6 rounded-lg border border-dashed border-border">
            <div className="flex w-12 h-12 p-2 justify-center items-center gap-2 rounded-md border border-border bg-white shadow-xs">
              <List size={24} />
            </div>
            <NoDataPage
              title={t('noDataPage.title')}
              subTitle={t('noDataPage.description')}
              isDisabled={isReadOnly}
              className="border-0 shadow-none p-0 items-center text-center"
            />
          </div>
        </ContentCard>
      ) : (
        <>
          <AccessManagementTable
            assignments={assignedGroups}
            onDeleteClick={id => handleDeleteGroup(id as string)}
            onAddRoleClick={handleAddRoleClick}
            onDeleteRole={handleDeleteRole}
            isReadOnly={isReadOnly}
            isLoading={isLoading}
          />
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
        </>
      )}
    </div>
  )

  const modals = (
    <>
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
            ? (assignedGroups.find(g => g.groupId === selectedGroupForRole)?.assignedRoles.map(r => r.roleId) ?? [])
            : []
        }
        onAddRoles={handleAddRoles}
        groupName={
          selectedGroupForRole ? (assignedGroups.find(g => g.groupId === selectedGroupForRole)?.groupName ?? '') : ''
        }
      />
    </>
  )

  // Controlled mode: no title, no subtitle, no action buttons
  if (isControlled) {
    return (
      <div data-testid={testId}>
        {mainContent}
        {modals}
      </div>
    )
  }

  // Uncontrolled mode: full standalone UI
  const EditButton = (
    <Button variant="outline" type="button" onClick={() => setIsReadOnlyInternal(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  const EditModeButtons = (
    <ActionButtons
      onCancelClick={handleCancel}
      onConfirmClick={onSubmit}
      confirmButtonType="button"
      confirmButtonTitle={tCommon('actions.submit')}
      cancelButtonTitle={tCommon('actions.exit')}
      isCancelButtonDisabled={false}
      isConfirmButtonDisabled={!hasChanges}
      hasCard={false}
    />
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader
        title={props.title as string}
        subtitle={props.subtitle as string}
        customElement={isReadOnly ? EditButton : EditModeButtons}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        {mainContent}
      </PageBackground>
      {modals}
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
