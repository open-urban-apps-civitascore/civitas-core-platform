'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/useQueryParams'

import { FormRole, ROLE_ORIGINS, RoleInput, RoleResponse, roleSchema, RoleUpdate } from '../../../../../types/roles'
import { DEFAULT_TAB } from '../page'
import { BaseInfoTab } from './baseinfo-tab/BaseInfoTab'
import { GroupAssignmentTab } from './group-assignment-tab/GroupAssignmentTab'
import { PermissionsTab } from './permissions-tab/PermissionsTab'

const defaultRole: RoleResponse = {
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

type Props = {
  roleId?: string
  isEditMode?: boolean
}

export const RoleDetails = (props: Props): JSX.Element => {
  const { roleId, isEditMode = false } = props
  const tRoles = useTranslations('roles')
  const router = useRouter()
  const queryClient = useQueryClient()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()
  const [hasPermissionsTabBeenSaved, setHasPermissionsTabBeenSaved] = useState<boolean>(false)

  const { data: roleData, isLoading: isLoadingRole } = useQuery({
    queryKey: ['role', roleId],
    queryFn: () =>
      apiRequest<RoleResponse>({
        endpoint: `/roles/${roleId}`,
        method: 'GET',
        errorMessage: 'An error occurred while loading role data',
      }),
    enabled: !!roleId,
  })

  const initialRole = roleData?.data || defaultRole

  const createRoleMutation = useMutation({
    mutationFn: (data: RoleInput) =>
      apiRequest<RoleResponse>({
        endpoint: '/roles',
        method: 'POST',
        data: data,
      }),
    onSuccess: ({ data }) => {
      console.log('Successfully created role')
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      router.push(`/roles/${data.id}?_tab=${tabValue}`)
    },
    onError: error => {
      console.error(`An error occurred while creating the role. ${error}`)
    },
  })

  const updateRoleMutation = useMutation({
    mutationFn: (data: RoleUpdate) =>
      apiRequest<RoleResponse>({
        endpoint: `/roles/${roleId}`,
        method: 'PUT',
        data: data,
      }),
    onSuccess: () => {
      setHasPermissionsTabBeenSaved(true)
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      queryClient.invalidateQueries({ queryKey: ['role', roleId] })
    },
    onError: error => {
      console.error(`An error occurred while updating the role. ${error}`)
    },
  })

  const deleteRoleMutation = useMutation({
    mutationFn: () =>
      apiRequest({
        endpoint: `/roles/${roleId}`,
        method: 'DELETE',
      }),
    onSuccess: () => {
      console.log('Successfully deleted role.')
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      router.push('/roles')
    },
    onError: error => {
      console.error(`An error occurred while deleting the role. ${error}`)
    },
  })

  const isLoading =
    isLoadingRole || createRoleMutation.isPending || updateRoleMutation.isPending || deleteRoleMutation.isPending

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  useEffect(() => {
    if (initialRole) {
      form.setValue('name', initialRole.name)
      form.setValue('description', initialRole.description || '')
    }
  }, [initialRole, form])

  const deleteRole = () => {
    deleteRoleMutation.mutate()
  }

  const onSubmit = (values: FormRole): void => {
    if (roleId && initialRole) {
      updateRoleMutation.mutate({ ...initialRole, ...values })
    } else {
      // eslint-disable-next-line unused-imports/no-unused-vars
      const { id, ...creadteRoleData } = { ...initialRole, ...values }
      createRoleMutation.mutate(creadteRoleData)
    }
  }

  const handlePermissionUpdate = (permissionIds: string[]): void => {
    if (!initialRole || !roleId) return

    updateRoleMutation.mutate({ ...initialRole, permissions: permissionIds })
  }

  const handleGroupAssigmentUpdate = (newGroupIds: string[]): void => {
    if (!initialRole || !roleId) return

    updateRoleMutation.mutate({ ...initialRole, groups: newGroupIds })
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
            deleteRole={() => roleId && deleteRole()}
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
