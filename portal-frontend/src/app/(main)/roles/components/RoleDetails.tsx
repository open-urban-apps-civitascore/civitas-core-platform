'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import {
  useCreateAssignment,
  useDeleteAssignment,
  useGetAssignments,
} from '@/app/services/api/assignments/clientRequests'
import { useCreateRole, useDeleteRole, useGetRole, useUpdateRole } from '@/app/services/api/roles/clientRequests'
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
  isEditMode?: boolean
}

export const RoleDetails = (props: RoleDetailsProps): JSX.Element => {
  const { roleId, isEditMode = false } = props
  const tRoles = useTranslations('roles')
  const tBaseInfo = useTranslations('roles.baseInfoTab')
  const router = useRouter()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()
  const [hasPermissionsTabBeenSaved, setHasPermissionsTabBeenSaved] = useState<boolean>(false)
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false)
  const [isDeleteErrorOpen, setIsDeleteErrorOpen] = useState(false)

  const deleteRole = useDeleteRole()
  const isRoleQueryEnabled = !!roleId && !deleteRole.isPending && !deleteRole.isSuccess

  const { data: roleData, isFetching: isLoadingRole } = useGetRole({ id: roleId || '', isEnabled: isRoleQueryEnabled })

  // Fetch existing assignments for this role so we can get assignment IDs for deletion
  const assignmentsParams = new URLSearchParams(`roleId=${roleId}`)
  const { data: assignmentsData, refetch: refetchAssignments } = useGetAssignments({
    params: assignmentsParams,
    isEnabled: !!roleId && isEditMode,
  })

  // Derive assigned group IDs directly from assignments (group.roles is not populated by backend)
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

  useEffect(() => {
    if (initialRole) {
      form.setValue('name', initialRole.name)
      form.setValue('description', initialRole.description || '')
      form.setValue('readonly', initialRole.readonly)
    }
  }, [initialRole, form])

  const handleDeleteRole = () => {
    setIsDeleteConfirmOpen(false)
    deleteRole.mutate(roleId || '', {
      onSuccess: () => router.push('/roles'),
      onError: () => {
        setIsDeleteErrorOpen(true)
      },
    })
  }

  const onSubmit = (values: FormRole): void => {
    if (roleId && initialRole) {
      updateRole.mutate(
        {
          id: roleId,
          name: values.name,
          description: values.description,
          roleType: initialRole.roleType,
          permissionIds: (initialRole.permissions ?? []).map(p => p.id),
          readonly: values.readonly,
        },
        {
          onSuccess: () => {
            setHasPermissionsTabBeenSaved(true)
          },
        },
      )
    } else {
      createRole.mutate(
        {
          name: values.name,
          description: values.description,
          roleType: (tabValue as Role['roleType']) || DEFAULT_TAB,
          readonly: values.readonly,
        },
        {
          onSuccess: ({ data }) => {
            router.push(`/roles/${data.id}?_tab=${tabValue}`)
          },
        },
      )
    }
  }

  const handlePermissionUpdate = (permissionIds: string[]): void => {
    if (!initialRole || !roleId) return

    updateRole.mutate(
      {
        id: roleId,
        name: initialRole.name,
        description: initialRole.description,
        roleType: initialRole.roleType,
        permissionIds,
        readonly: initialRole.readonly,
      },
      {
        onSuccess: () => {
          setHasPermissionsTabBeenSaved(true)
        },
      },
    )
  }

  const handleGroupAssigmentUpdate = async (newGroupIds: string[]): Promise<void> => {
    if (!roleId) return

    const existingAssignments = assignmentsData?.data ?? []
    const currentGroupIds = existingAssignments.map(a => a.group.id)

    const groupIdsToAdd = newGroupIds.filter(id => !currentGroupIds.includes(id))
    const assignmentsToRemove = existingAssignments.filter(a => !newGroupIds.includes(a.group.id))

    // Create new assignments
    await Promise.all(groupIdsToAdd.map(groupId => createAssignment.mutateAsync({ groupId, roleId })))

    // Delete removed assignments
    await Promise.all(assignmentsToRemove.map(a => deleteAssignment(a.id)))

    // Refresh assignments so the tab reflects the new state
    await refetchAssignments()
  }

  const disabledTabs = !isEditMode ? ['permissions', 'groupAssignment'] : undefined

  const subTabs: Tab<RoleTab>[] = [
    subTabValues.basicInformation,
    subTabValues.permissions,
    subTabValues.groupAssignment,
  ]
  const defaultSubTab = subTabValues.basicInformation.value
  const isDefaultRole = initialRole?.readonly === true

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(defaultSubTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultSubTab])

  const roleType = roleId ? initialRole?.roleType : tabValue || DEFAULT_TAB
  const badgeTitle = roleType ? tRoles(`${roleType.toLowerCase()}Roles`).slice(0, -1) : undefined

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
      />

      <PageBackground className="overflow-auto">
        {subTabValue === subTabValues.basicInformation.value && (
          <BaseInfoTab
            form={form}
            onSubmit={onSubmit}
            isLoading={isLoading}
            roleType={tabValue}
            isDefaultRole={isDefaultRole}
            isEditMode={isEditMode}
            deleteRole={() => roleId && setIsDeleteConfirmOpen(true)}
          />
        )}

        {subTabValue === subTabValues.permissions.value && (
          <PermissionsTab
            onPermissionUpdate={handlePermissionUpdate}
            currentSelectedPermissionIds={initialRole?.permissions?.map(p => p.id) ?? []}
            roleType={tabValue}
            hasPermissionsTabBeenSaved={hasPermissionsTabBeenSaved}
            setHasPermissionsTabBeenSaved={setHasPermissionsTabBeenSaved}
            isDefaultRole={isDefaultRole}
          />
        )}

        {subTabValue === subTabValues.groupAssignment.value && (
          <GroupAssignmentTab
            assignedGroupIds={assignedGroupIds}
            onGroupAssignmentUpdate={handleGroupAssigmentUpdate}
            roleName={initialRole?.name || ''}
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
