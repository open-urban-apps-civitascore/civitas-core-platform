'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useCreateRole, useDeleteRole, useGetRole, useUpdateRole } from '@/app/services/api/roles/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/use-query-params'
import { FormRole, Role, ROLE_ORIGINS, roleSchema } from '@/types/roles'

import { DEFAULT_TAB } from '../page'
import { BaseInfoTab } from './baseinfo-tab/BaseInfoTab'
import { GroupAssignmentTab } from './group-assignment-tab/GroupAssignmentTab'
import { PermissionsTab } from './permissions-tab/PermissionsTab'

const defaultRole: Role = {
  id: '',
  name: '',
  type: DEFAULT_TAB,
  tenant: '',
  users: [],
  permissions: [],
  groups: [],
  createdAt: new Date().toISOString(), // Placeholder createdAt, later set by backend
  lastUpdated: null,
  updatedBy: null,
  roleOrigin: ROLE_ORIGINS.CUSTOM,
}

interface RoleDetailsProps {
  roleId?: string
  isEditMode?: boolean
}

export const RoleDetails = (props: RoleDetailsProps): JSX.Element => {
  const { roleId, isEditMode = false } = props
  const tRoles = useTranslations('roles')
  const router = useRouter()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()
  const [hasPermissionsTabBeenSaved, setHasPermissionsTabBeenSaved] = useState<boolean>(false)

  const { data: roleData, isFetching: isLoadingRole } = useGetRole({ id: roleId || '', isEnabled: !!roleId })

  const initialRole = roleData?.data || defaultRole

  const createRole = useCreateRole()
  const updateRole = useUpdateRole()
  const deleteRole = useDeleteRole(roleId || '')

  const isLoading = isLoadingRole || createRole.isPending || updateRole.isPending || deleteRole.isPending

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: initialRole.name,
      description: initialRole.description,
    },
  })

  useEffect(() => {
    if (initialRole) {
      form.setValue('name', initialRole.name)
      form.setValue('description', initialRole.description || '')
    }
  }, [initialRole, form])

  const handleDeleteRole = () => {
    deleteRole.mutate(undefined, {
      onSuccess: () => router.push('/roles'),
    })
  }

  const onSubmit = (values: FormRole): void => {
    if (roleId && initialRole) {
      updateRole.mutate(
        { ...initialRole, ...values },
        {
          onSuccess: () => {
            setHasPermissionsTabBeenSaved(true)
          },
        },
      )
    } else {
      // eslint-disable-next-line unused-imports/no-unused-vars
      const { id, ...creadteRoleData } = { ...initialRole, ...values }
      createRole.mutate(creadteRoleData, {
        onSuccess: ({ data }) => {
          router.push(`/roles/${data.id}?_tab=${tabValue}`)
        },
      })
    }
  }

  const handlePermissionUpdate = (permissionIds: string[]): void => {
    if (!initialRole || !roleId) return

    updateRole.mutate(
      { ...initialRole, permissions: permissionIds },
      {
        onSuccess: () => {
          setHasPermissionsTabBeenSaved(true)
        },
      },
    )
  }

  const handleGroupAssigmentUpdate = (newGroupIds: string[]): void => {
    if (!initialRole || !roleId) return

    updateRole.mutate(
      { ...initialRole, groups: newGroupIds },
      {
        onSuccess: () => {
          setHasPermissionsTabBeenSaved(true)
        },
      },
    )
  }

  const subTabValues: Record<'basicInformation' | 'permissions' | 'groupAssignment', Tab> = {
    basicInformation: {
      label: tRoles('tabLabels.basicInformation'),
      value: 'basicInformation',
      isActive: true,
    },
    permissions: {
      label: tRoles('tabLabels.permissions'),
      value: 'permissions',
      isActive: isEditMode,
    },
    groupAssignment: {
      label: tRoles('tabLabels.groupAssignment'),
      value: 'groupAssignment',
      isActive: isEditMode,
    },
  }

  const subTabs: Tab[] = [subTabValues.basicInformation, subTabValues.permissions, subTabValues.groupAssignment]
  const defaultSubTab = subTabValues.basicInformation.value
  const isDefaultRole = initialRole?.roleOrigin === ROLE_ORIGINS.DEFAULT

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(defaultSubTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultSubTab])

  const roleType = initialRole?.type || tabValue
  const badgeTitle = roleType ? tRoles(`${roleType}Roles`).slice(0, -1) : undefined

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={roleId ? initialRole?.name : tRoles('newRole')}
        badgeTitle={badgeTitle}
        subTabs={{
          tabs: subTabs,
          selectedTab: subTabValue || defaultSubTab,
          onClick: newSubTab => setSubTabValueParam(newSubTab),
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
            deleteRole={() => roleId && handleDeleteRole()}
          />
        )}

        {subTabValue === subTabValues.permissions.value && (
          <PermissionsTab
            onPermissionUpdate={handlePermissionUpdate}
            currentSelectedPermissionIds={initialRole?.permissions || []}
            roleType={tabValue}
            hasPermissionsTabBeenSaved={hasPermissionsTabBeenSaved}
            setHasPermissionsTabBeenSaved={setHasPermissionsTabBeenSaved}
            isDefaultRole={isDefaultRole}
          />
        )}

        {subTabValue === subTabValues.groupAssignment.value && (
          <GroupAssignmentTab
            assignedGroupIds={initialRole?.groups || []}
            onGroupAssignmentUpdate={handleGroupAssigmentUpdate}
            roleName={initialRole?.name || ''}
          />
        )}
      </PageBackground>
    </PageContainer>
  )
}
