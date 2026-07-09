'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateAssignment,
  useDeleteAssignment,
  useGetAssignments,
} from '@/app/services/api/assignments/clientRequests'
import { useCreateRole, useDeleteRole, useGetRole, useUpdateRole } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { useError } from '@/hooks/use-error'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { FormRole, Role, ROLE_TYPES, RoleSchema, RoleTab } from '@/types/roles'
import { isPlatformwideAssignment } from '@/utils/assignments'
import { isNameConflictError, isPermissionsError } from '@/utils/errors'

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
  const { hasPermission } = usePermissions()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()
  const { handleFormValidationError, handleNameError, handlePermissionsError } = useError()

  const canUpdate = !roleId || hasPermission(PERMISSION_NAMES.ROLE_UPDATE)
  const canDelete = hasPermission(PERMISSION_NAMES.ROLE_DELETE)
  const canReadAssignments = hasPermission(PERMISSION_NAMES.ASSIGNMENT_READ)

  const [isReadOnly, setIsReadOnly] = useState(!!roleId)
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const [pendingPermissionIds, setPendingPermissionIds] = useState<string[]>([])
  const tBaseInfo = useTranslations('roles.baseInfoTab')
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false)
  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false)
  const [selectedGroupIds, setSelectedGroupIds] = useState<string[]>([])

  const deleteRole = useDeleteRole()
  const isRoleQueryEnabled = !!roleId && !deleteRole.isPending && !deleteRole.isSuccess

  const isGroupTab = subTabValue === subTabValues.groupAssignment.value

  const {
    data: roleData,
    isFetching: isLoadingRole,
    refetch: refreshRole,
    error: getRoleError,
  } = useGetRole({ id: roleId || '', isEnabled: isRoleQueryEnabled })

  // Fetch existing assignments for this role
  const assignmentsParams = new URLSearchParams(`roleId=${roleId}`)
  const {
    data: assignmentsData,
    refetch: refetchAssignments,
    isFetching: isLoadingAssignments,
    error: getAssignmentsError,
  } = useGetAssignments({
    params: assignmentsParams,
    isEnabled: !!roleId && canReadAssignments,
  })

  const initialPlatformAssignments = useMemo(
    () => (assignmentsData?.data ?? []).filter(assignment => isPlatformwideAssignment(assignment)),
    [assignmentsData?.data],
  )

  const initiallyAssignedGroupsIds = useMemo(
    () => initialPlatformAssignments.map(assignment => assignment.group.id),
    [initialPlatformAssignments],
  )

  useEffect(() => {
    setSelectedGroupIds(initiallyAssignedGroupsIds)
  }, [initiallyAssignedGroupsIds])

  const createAssignment = useCreateAssignment()
  const deleteAssignment = useDeleteAssignment()

  const initialRole = roleData?.data || defaultRole
  const isCreateMode = !(roleId && initialRole)

  const createRole = useCreateRole()
  const updateRole = useUpdateRole()

  const isLoading =
    isLoadingRole ||
    isLoadingAssignments ||
    createRole.isPending ||
    updateRole.isPending ||
    deleteRole.isPending ||
    createAssignment.isPending ||
    deleteAssignment.isPending

  const mapRoleApiToFormData = (role: Role): FormRole => ({ ...role, description: role.description || '' })

  const form = useForm<FormRole>({
    resolver: zodResolver(RoleSchema),
    defaultValues: mapRoleApiToFormData(initialRole),
  })

  const handleRoleRequestError = (error: AxiosError, defaultMessage: string) => {
    if (isNameConflictError(error)) {
      handleNameError(form, form.getValues('name'))
      return
    }
    if (isPermissionsError(error)) {
      handlePermissionsError()
      return
    }
    toast.error(defaultMessage)
  }

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
    () => {
      return !initialPlatformAssignments
        ? false
        : !(
            (initialPlatformAssignments.length || 0) === selectedGroupIds.length &&
            initialPlatformAssignments.every(assignment => selectedGroupIds.includes(assignment.group.id))
          )
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedGroupIds],
  )

  const arePermissionsDirty = useMemo(() => {
    const initialSet = new Set(initialPermissionIds)
    const pendingSet = new Set(pendingPermissionIds)
    if (initialSet.size !== pendingSet.size) return true
    return Array.from(initialSet).some(id => !pendingSet.has(id))
  }, [pendingPermissionIds, initialPermissionIds])

  const isAnyDirty = form.formState.isDirty || arePermissionsDirty || areAssignmentsDirty

  const handleDeleteClick = () => {
    if ((assignmentsData?.data?.length ?? 0) > 0) {
      setIsInfoModalOpen(true)
    } else {
      setIsDeleteConfirmOpen(true)
    }
  }

  const handleDeleteRole = () => {
    setIsDeleteConfirmOpen(false)
    deleteRole.mutate(roleId || '', {
      onSuccess: () => {
        toast.success(tCommon('success.deletionSuccess', { item: tCommon('items.role') }))
        router.push('/roles')
      },
      onError: error => {
        if (isPermissionsError(error as AxiosError)) handlePermissionsError()
        else toast.error(tRoles('errors.deleteError'))
      },
    })
  }

  const createNewRole = async (formValues: FormRole): Promise<boolean> => {
    try {
      const { data } = await createRole.mutateAsync({
        name: formValues.name,
        description: formValues.description,
        roleType: (tabValue as Role['roleType']) || DEFAULT_TAB,
        permissionIds: pendingPermissionIds,
        readonly: formValues.readonly,
      })
      toast.success(tCommon('success.creationSuccess', { item: tCommon('items.role') }))
      router.push(`/roles/${data.id}?tab=${tabValue}`)
      return true
    } catch (error) {
      handleRoleRequestError(error as AxiosError, tRoles('errors.createError'))
      return false
    }
  }

  const updateRoleValues = async (formValues: FormRole, roleId: string) => {
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
      handleRoleRequestError(error as AxiosError, tRoles('errors.updateError'))
      throw error
    }
  }

  const saveGroupAssignment = async (roleId: string): Promise<void> => {
    try {
      const groupIdsToAdd = selectedGroupIds.filter(id => !initiallyAssignedGroupsIds.includes(id))
      const assignmentsToRemove = initialPlatformAssignments.filter(a => !selectedGroupIds.includes(a.group.id))

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
      void refetchAssignments()
      toast.success(tRoles('success.assignmentSuccess'))
    } catch (error) {
      if (isPermissionsError(error as AxiosError)) handlePermissionsError()
      else {
        console.error('An error occurred while groups assignment')
        toast.error(tCommon('errors.updateError', { item: tCommon('items.assignments') }))
      }
      throw error
    }
  }

  const updateRoleAndAssignments = async (formValues: FormRole, roleId: string): Promise<boolean> => {
    const shouldUpdateValues = form.formState.isDirty || arePermissionsDirty
    try {
      // values of default roles must not be edited but their assignments can be updated
      if (!isDefaultRole && shouldUpdateValues) await updateRoleValues(formValues, roleId)
      if (areAssignmentsDirty) await saveGroupAssignment(roleId)
      setIsExitModalOpen(false)
      return true
      // eslint-disable-next-line unused-imports/no-unused-vars
    } catch (error) {
      console.error('An error occurred while updating the role')
      return false
    }
  }

  const handleSave = async () => {
    let isSaved = false

    await form.handleSubmit(
      async formValues => {
        isSaved = isCreateMode
          ? await createNewRole(formValues)
          : await updateRoleAndAssignments(formValues, roleId as string)
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()

    return isSaved
  }

  useRegisterUnsavedChanges(isAnyDirty, handleSave)

  const handleExit = () => {
    if (!roleId) {
      router.push('/roles')
      return
    }
    form.reset(mapRoleApiToFormData(initialRole))
    setPendingPermissionIds(initialPermissionIds)
    setSelectedGroupIds(initiallyAssignedGroupsIds)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (isAnyDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleGroupAssignmentUpdate = (newGroupIds: string[]) => {
    setSelectedGroupIds(newGroupIds)
  }

  // Tab configuration
  // - Permission-gated tabs are hidden (no affordance = no confusion)
  // - State-gated tabs (unsaved role) are disabled (visible but greyed out)
  const disabledTabs = !roleId ? ['groupAssignment'] : undefined

  const subTabs: Tab<RoleTab>[] = [
    subTabValues.basicInformation,
    ...(hasPermission(PERMISSION_NAMES.PERMISSION_READ) ? [subTabValues.permissions] : []),
    ...(hasPermission(PERMISSION_NAMES.ASSIGNMENT_READ) && hasPermission(PERMISSION_NAMES.GROUP_READ)
      ? [subTabValues.groupAssignment]
      : []),
  ]
  const defaultSubTab = subTabValues.basicInformation.value

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(defaultSubTab, true)
    }
  }, [subTabValue, setSubTabValueParam, defaultSubTab])

  const roleType = roleId ? initialRole?.roleType : tabValue || DEFAULT_TAB
  const badgeTitle = roleType ? tRoles(`${roleType.toLowerCase()}Roles`).slice(0, -1) : undefined

  const SaveAndExitButtons = (
    <ActionButtons
      confirmButtonType="button"
      onCancelClick={handleExitButtonClick}
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={!isAnyDirty || isLoading}
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
    if (isReadOnly) return canUpdate ? EditButton : undefined
    return SaveAndExitButtons
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
                deleteRole={roleId && canDelete ? handleDeleteClick : undefined}
                getRoleError={getRoleError}
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
                selectedGroupIds={selectedGroupIds}
                onGroupAssignmentUpdate={handleGroupAssignmentUpdate}
                roleName={initialRole?.name || ''}
                isSystemRole={initialRole.roleType === ROLE_TYPES.SYSTEM}
                isReadOnly={isReadOnly}
                initialAssignments={assignmentsData?.data ?? []}
                getAssignmentsError={getAssignmentsError}
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

      <InfoModal
        open={isInfoModalOpen}
        title={tBaseInfo('infoModal.title')}
        description={tBaseInfo('infoModal.description')}
        onClose={() => setIsInfoModalOpen(false)}
      />

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
