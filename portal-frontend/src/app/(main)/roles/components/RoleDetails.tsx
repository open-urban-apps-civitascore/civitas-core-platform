'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import {
  useCreateAssignment,
  useDeleteAssignment,
  useGetAssignments,
} from '@/app/services/api/assignments/clientRequests'
import { useCreateRole, useDeleteRole, useGetRole, useUpdateRole } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/use-query-params'
import { FormRole, Role, roleSchema, RoleTab } from '@/types/roles'

import { DEFAULT_TAB } from '../page'
import { BaseInfoTab } from './baseinfo-tab/BaseInfoTab'
import { GroupAssignmentTab } from './group-assignment-tab/GroupAssignmentTab'
import { PermissionsTab } from './permissions-tab/PermissionsTab'

const subTabValues: Record<RoleTab, Tab<RoleTab>> = {
  basicInformation: {
    label: 'roles.tabLabels.basicInformation',
    value: 'basicInformation',
  },
  permissions: {
    label: 'roles.tabLabels.permissions',
    value: 'permissions',
  },
  groupAssignment: {
    label: 'roles.tabLabels.groupAssignment',
    value: 'groupAssignment',
  },
}

const defaultRole: Role = {
  id: '',
  name: '',
  roleType: DEFAULT_TAB,
  permissions: [],
  readonly: false,
  modifiedBy: null,
  modifiedAt: null,
  createdAt: new Date().toISOString(),
  groupCount: 0,
  userCount: 0,
}

interface RoleDetailsProps {
  roleId?: string
}

export const RoleDetails = (props: RoleDetailsProps): JSX.Element => {
  const { roleId } = props
  const tRoles = useTranslations('roles')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()

  const [isReadOnly, setIsReadOnly] = useState(!!roleId)
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const [pendingPermissionIds, setPendingPermissionIds] = useState<string[]>([])
  const tBaseInfo = useTranslations('roles.baseInfoTab')
  const [hasPermissionsTabBeenSaved, setHasPermissionsTabBeenSaved] = useState<boolean>(false)
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false)
  const [isDeleteErrorOpen, setIsDeleteErrorOpen] = useState(false)

  const deleteRole = useDeleteRole()
  const isRoleQueryEnabled = !!roleId && !deleteRole.isPending && !deleteRole.isSuccess

  const { data: roleData, isFetching: isLoadingRole } = useGetRole({ id: roleId || '', isEnabled: isRoleQueryEnabled })

  // Fetch existing assignments for this role
  const assignmentsParams = new URLSearchParams(`roleId=${roleId}`)
  const { data: assignmentsData, refetch: refetchAssignments } = useGetAssignments({
    params: assignmentsParams,
    isEnabled: !!roleId,
  })

  const assignedGroupIds = (assignmentsData?.data ?? []).map(a => a.group.id)

  const createAssignment = useCreateAssignment()
  const deleteAssignment = useDeleteAssignment()

  const initialRole = roleData?.data || defaultRole

  const createRole = useCreateRole()
  const updateRole = useUpdateRole()

  const isLoading = isLoadingRole || createRole.isPending || updateRole.isPending || deleteRole.isPending

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: initialRole.name,
      description: initialRole.description,
      readonly: initialRole.readonly,
    },
  })

  // Initialize form and permissions from role data
  useEffect(() => {
    if (initialRole) {
      form.setValue('name', initialRole.name)
      form.setValue('description', initialRole.description || '')
      form.setValue('readonly', initialRole.readonly)
      setPendingPermissionIds((initialRole.permissions ?? []).map(p => p.id))
    }
  }, [initialRole, form])

  const isDefaultRole = initialRole?.readonly === true

  // Track dirty state for permissions
  const initialPermissionIds = useMemo(
    () => (initialRole?.permissions ?? []).map(p => p.id),
    [initialRole?.permissions],
  )

  const arePermissionsDirty = useMemo(() => {
    const initialSet = new Set(initialPermissionIds)
    const pendingSet = new Set(pendingPermissionIds)
    if (initialSet.size !== pendingSet.size) return true
    return Array.from(initialSet).some(id => !pendingSet.has(id))
  }, [pendingPermissionIds, initialPermissionIds])

  const isAnyDirty = form.formState.isDirty || arePermissionsDirty

  const handleDeleteRole = () => {
    setIsDeleteConfirmOpen(false)
    deleteRole.mutate(roleId || '', {
      onSuccess: () => router.push('/roles'),
      onError: () => {
        setIsDeleteErrorOpen(true)
      },
    })
  }

  // Unified save handler
  const handleSave = useCallback(() => {
    const formValues = form.getValues()

    if (roleId && initialRole) {
      updateRole.mutate(
        {
          id: roleId,
          name: formValues.name,
          description: formValues.description,
          roleType: initialRole.roleType,
          permissionIds: pendingPermissionIds,
          readonly: formValues.readonly,
        },
        {
          onSuccess: () => {
            setIsReadOnly(true)
            setIsExitModalOpen(false)
            router.refresh()
          },
        },
      )
    } else {
      createRole.mutate(
        {
          name: formValues.name,
          description: formValues.description,
          roleType: (tabValue as Role['roleType']) || DEFAULT_TAB,
          readonly: formValues.readonly,
        },
        {
          onSuccess: ({ data }) => {
            router.push(`/roles/${data.id}?tab=${tabValue}`)
          },
        },
      )
    }
  }, [roleId, initialRole, form, pendingPermissionIds, updateRole, createRole, tabValue, router])

  // Exit edit mode
  const handleExit = useCallback(() => {
    form.reset()
    setPendingPermissionIds(initialPermissionIds)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }, [form, initialPermissionIds])

  const handleExitButtonClick = useCallback(() => {
    if (isAnyDirty) setIsExitModalOpen(true)
    else handleExit()
  }, [isAnyDirty, handleExit])

  // Group assignment (immediate - not part of unified save)
  const handleGroupAssignmentUpdate = async (newGroupIds: string[]): Promise<void> => {
    if (!roleId) return

    const existingAssignments = assignmentsData?.data ?? []
    const currentGroupIds = existingAssignments.map(a => a.group.id)

    const groupIdsToAdd = newGroupIds.filter(id => !currentGroupIds.includes(id))
    const assignmentsToRemove = existingAssignments.filter(a => !newGroupIds.includes(a.group.id))

    await Promise.all(
      groupIdsToAdd.map(groupId =>
        createAssignment.mutateAsync({
          groupId,
          roleId,
          ...(initialRole.roleType !== 'SYSTEM' && { scopeType: 'TENANT' as const }),
        }),
      ),
    )
    await Promise.all(assignmentsToRemove.map(a => deleteAssignment(a.id)))
    await refetchAssignments()
  }

  // Tab configuration
  const disabledTabs = !roleId ? ['permissions', 'groupAssignment'] : undefined

  const subTabs: Tab<RoleTab>[] = [
    subTabValues.basicInformation,
    subTabValues.permissions,
    subTabValues.groupAssignment,
  ]
  const defaultSubTab = subTabValues.basicInformation.value

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(defaultSubTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultSubTab])

  const roleType = roleId ? initialRole?.roleType : tabValue || DEFAULT_TAB
  const badgeTitle = roleType ? tRoles(`${roleType.toLowerCase()}Roles`).slice(0, -1) : undefined

  // Header custom elements
  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  const ActionButtonsElement = (
    <ActionButtons
      confirmButtonType="button"
      onCancelClick={handleExitButtonClick}
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={!isAnyDirty || isLoading}
      isCancelButtonDisabled={isLoading}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      wrapperClassname="w-auto"
    />
  )

  const getCustomElement = () => {
    if (isDefaultRole) return undefined
    if (!roleId) return ActionButtonsElement
    if (isReadOnly) return EditButton
    return ActionButtonsElement
  }

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={roleId ? initialRole?.name : tRoles('newRole')}
        badgeTitle={badgeTitle}
        segmentedControlBarProps={{
          tabs: subTabs,
          selectedTab: subTabValue || defaultSubTab,
          onTabChange: newSubTab => setSubTabValueParam(newSubTab),
          disabledTabs,
        }}
        customElement={getCustomElement()}
      />

      <PageBackground className="overflow-auto" hasBackground={!isReadOnly}>
        {subTabValue === subTabValues.basicInformation.value && (
          <BaseInfoTab
            form={form}
            isDefaultRole={isDefaultRole}
            isReadOnly={isReadOnly || isDefaultRole}
            deleteRole={() => roleId && setIsDeleteConfirmOpen(true)}
          />
        )}

        {subTabValue === subTabValues.permissions.value && (
          <PermissionsTab
            pendingPermissionIds={pendingPermissionIds}
            onPendingPermissionIdsChange={setPendingPermissionIds}
            isReadOnly={isReadOnly || isDefaultRole}
            currentRoleId={roleId}
            roleType={(roleType as Role['roleType']) || DEFAULT_TAB}
          />
        )}

        {subTabValue === subTabValues.groupAssignment.value && (
          <GroupAssignmentTab
            assignedGroupIds={assignedGroupIds}
            onGroupAssignmentUpdate={handleGroupAssignmentUpdate}
            roleName={initialRole?.name || ''}
            isEditMode={!isReadOnly && !isDefaultRole}
            assignments={assignmentsData?.data ?? []}
          />
        )}
      </PageBackground>

      <WarningModal
        title={tBaseInfo('deleteConfirmModal.title')}
        description={tBaseInfo('deleteConfirmModal.description')}
        open={isDeleteConfirmOpen}
        onOpenChange={setIsDeleteConfirmOpen}
        onDiscard={() => setIsDeleteConfirmOpen(false)}
        onConfirm={handleDeleteRole}
        confirmButtonTitle={tBaseInfo('deleteConfirmModal.confirm')}
        isLoading={deleteRole.isPending}
      />

      <Dialog open={isDeleteErrorOpen} onOpenChange={setIsDeleteErrorOpen}>
        <DialogContent className="sm:max-w-md" showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{tBaseInfo('deleteErrorModal.title')}</DialogTitle>
            <DialogDescription>{tBaseInfo('deleteErrorModal.description')}</DialogDescription>
          </DialogHeader>
          <DialogFooter className="flex sm:justify-end">
            <Button type="button" onClick={() => setIsDeleteErrorOpen(false)}>
              {tBaseInfo('deleteErrorModal.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  )
}
