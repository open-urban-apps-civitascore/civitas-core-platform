'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateAssignment,
  useDeleteAssignment,
  useGetAssignments,
} from '@/app/services/api/assignments/clientRequests'
import { useCreateRole, useDeleteRole, useGetRole, useUpdateRole } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
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
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { FormRole, Role, roleSchema, RoleTab } from '@/types/roles'

import { DEFAULT_TAB } from '../page'
import { BaseInfoTab } from './baseinfo-tab/BaseInfoTab'
import { GroupAssignmentTab } from './group-assignment-tab/GroupAssignmentTab'
import { PermissionsTab } from './permissions-tab/PermissionsTab'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'

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
  description: '',
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
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false)
  const [isDeleteErrorOpen, setIsDeleteErrorOpen] = useState(false)
  const [selectedGroupAssignmentIds, setSelectedGroupAssignmentIds] = useState<string[]>([])

  const deleteRole = useDeleteRole()
  const isRoleQueryEnabled = !!roleId && !deleteRole.isPending && !deleteRole.isSuccess

  const isGroupTab = subTabValue === subTabValues.groupAssignment.value

  const {
    data: roleData,
    isFetching: isLoadingRole,
    refetch: refreshRole,
  } = useGetRole({ id: roleId || '', isEnabled: isRoleQueryEnabled })

  // Fetch existing assignments for this role
  const assignmentsParams = new URLSearchParams(`roleId=${roleId}`)
  const { data: assignmentsData, refetch: refetchAssignments } = useGetAssignments({
    params: assignmentsParams,
    isEnabled: !!roleId,
  })

  useEffect(() => {
    setSelectedGroupAssignmentIds(
      (assignmentsData?.data ?? []).flatMap(a =>
        a.scopeType === ASSIGNMENT_SCOPE_TYPES.TENANT || a.scopeType === null ? a.group.id : [],
      ),
    )
  }, [assignmentsData])

  const createAssignment = useCreateAssignment()
  const deleteAssignment = useDeleteAssignment()

  const initialRole = roleData?.data || defaultRole

  const createRole = useCreateRole()
  const updateRole = useUpdateRole()

  const isLoading =
    isLoadingRole ||
    createRole.isPending ||
    updateRole.isPending ||
    deleteRole.isPending ||
    createAssignment.isPending ||
    deleteAssignment.isPending

  const mapRoleApiToFormData = (role: Role): FormRole => ({ ...role, description: role.description || '' })

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: mapRoleApiToFormData(initialRole),
  })

  // Initialize form and permissions from role data
  useEffect(() => {
    if (initialRole) {
      form.reset(mapRoleApiToFormData(initialRole))
      setPendingPermissionIds((initialRole.permissions ?? []).map(p => p.id))
    }
  }, [initialRole, form])

  const isDefaultRole = initialRole?.readonly === true

  const initialPermissionIds = useMemo(
    () => (initialRole?.permissions ?? []).map(p => p.id),
    [initialRole?.permissions],
  )

  const areAssignmentsDirty = useMemo(
    () =>
      !assignmentsData?.data
        ? false
        : !(
            (assignmentsData?.data.length || 0) === selectedGroupAssignmentIds.length &&
            assignmentsData?.data.every(assignment => selectedGroupAssignmentIds.includes(assignment.group.id))
          ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedGroupAssignmentIds],
  )

  const arePermissionsDirty = useMemo(() => {
    const initialSet = new Set(initialPermissionIds)
    const pendingSet = new Set(pendingPermissionIds)
    if (initialSet.size !== pendingSet.size) return true
    return Array.from(initialSet).some(id => !pendingSet.has(id))
  }, [pendingPermissionIds, initialPermissionIds])

  const isAnyDirty = form.formState.isDirty || arePermissionsDirty || areAssignmentsDirty

  const handleDeleteRole = () => {
    setIsDeleteConfirmOpen(false)
    deleteRole.mutate(roleId || '', {
      onSuccess: () => router.push('/roles'),
      onError: () => {
        setIsDeleteErrorOpen(true)
      },
    })
  }

  const createNewRole = () => {
    const formValues = form.getValues()

    createRole.mutate(
      {
        name: formValues.name,
        description: formValues.description,
        roleType: (tabValue as Role['roleType']) || DEFAULT_TAB,
        readonly: formValues.readonly,
      },
      {
        onSuccess: ({ data }) => {
          toast.success(tCommon('success.createSuccess', { item: tCommon('items.role') }))
          router.push(`/roles/${data.id}?tab=${tabValue}`)
        },
        onError: error => {
          console.error('An error occurred while creating the role', error)
          toast.error(tRoles('errors.createError'))
        },
      },
    )
  }

  const updateRoleValues = async (roleId: string) => {
    const formValues = form.getValues()

    try {
      await updateRole.mutateAsync({
        id: roleId,
        name: formValues.name,
        description: formValues.description,
        roleType: initialRole.roleType,
        permissionIds: pendingPermissionIds,
        readonly: formValues.readonly,
      })
      refreshRole()
      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.role') }))
    } catch (error) {
      console.error('An error occurred while updating the role values.', error)
      toast.error(tCommon('errors.updateError', { item: tCommon('items.role') }))
      throw error
    }
  }

  const saveGroupAssignment = async (roleId: string): Promise<void> => {
    try {
      const existingAssignments = assignmentsData?.data ?? []
      const currentGroupIds = existingAssignments.flatMap(a =>
        a.scopeType === null || a.scopeType === ASSIGNMENT_SCOPE_TYPES.TENANT ? a.group.id : [],
      )

      const groupIdsToAdd = selectedGroupAssignmentIds.filter(id => !currentGroupIds.includes(id))
      const assignmentsToRemove = existingAssignments.filter(a => !selectedGroupAssignmentIds.includes(a.group.id))

      await Promise.all(
        groupIdsToAdd.map(groupId =>
          createAssignment.mutateAsync({
            groupId,
            roleId,
            ...(initialRole.roleType !== 'SYSTEM' && { scopeType: ASSIGNMENT_SCOPE_TYPES.TENANT }),
          }),
        ),
      )
      await Promise.all(assignmentsToRemove.map(a => deleteAssignment.mutateAsync(a.id)))
      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.assignments') }))
      await refetchAssignments()
    } catch (error) {
      console.error('An error occurred while groups assignment')
      toast.error(tCommon('errors.updateError', { item: tCommon('items.assignments') }))
      throw error
    }
  }

  // Unified save handler
  const handleSave = async () => {
    const isCreateMode = !(roleId && initialRole)

    if (isCreateMode) {
      createNewRole()
    } else {
      const shouldUpdateValues = form.formState.isDirty || arePermissionsDirty
      try {
        // values of default roles must not be edited but their assignments can be updated
        if (!isDefaultRole && shouldUpdateValues) await updateRoleValues(roleId)
        if (areAssignmentsDirty) await saveGroupAssignment(roleId)
        setIsExitModalOpen(false)
      } catch (error) {
        console.error('An error occurred while updating the role')
      }
    }
  }

  const handleExit = useCallback(() => {
    if (!roleId) {
      router.push('/roles')
      return
    }
    form.reset({
      name: initialRole.name,
      description: initialRole.description || '',
      readonly: initialRole.readonly,
    })
    setPendingPermissionIds(initialPermissionIds)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }, [form, initialRole, initialPermissionIds, roleId, router])

  const handleExitButtonClick = useCallback(() => {
    if (isAnyDirty) setIsExitModalOpen(true)
    else handleExit()
  }, [isAnyDirty, handleExit])

  const handleGroupAssignmentUpdate = (newGroupIds: string[]) => {
    setSelectedGroupAssignmentIds(newGroupIds)
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

  const SaveAndExitButtons = (
    <ActionButtons
      confirmButtonType="button"
      onCancelClick={handleExitButtonClick}
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={(!isAnyDirty && !areAssignmentsDirty) || isLoading}
      isCancelButtonDisabled={isLoading}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      className="px-6 py-0"
      wrapperClassname="w-auto"
    />
  )

  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  const getButtons = () => {
    if (isDefaultRole && !isGroupTab) return undefined
    if (isReadOnly) return EditButton
    else return SaveAndExitButtons
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
        customElement={getButtons()}
      />

      <PageBackground className="overflow-auto" hasBackground={!isReadOnly}>
        {isLoading ? (
          <LoadingSpinner className="h-full" />
        ) : (
          <>
            {subTabValue === subTabValues.basicInformation.value && (
              <BaseInfoTab
                form={form}
                isDefaultRole={isDefaultRole}
                isReadOnly={isReadOnly || isDefaultRole}
                deleteRole={roleId ? () => setIsDeleteConfirmOpen(true) : undefined}
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
                selectedGroupIds={selectedGroupAssignmentIds}
                onGroupAssignmentUpdate={handleGroupAssignmentUpdate}
                roleName={initialRole?.name || ''}
                isReadOnly={isReadOnly}
                initialAssignments={assignmentsData?.data ?? []}
              />
            )}
          </>
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

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
