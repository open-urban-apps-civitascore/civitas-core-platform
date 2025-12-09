'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { JSX, useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/useQueryParams'

import {
  FormRole,
  ROLE_ORIGINS,
  RoleInput,
  RoleResponse,
  roleSchema,
  RoleType,
  RoleUpdate,
} from '../../../../../types/roles'
import { DEFAULT_TAB } from '../page'
import { BaseInfoTab } from './baseinfo-tab/BaseInfoTab'
import { GroupAssignmentTab } from './group-assignment-tab/GroupAssignmentTab'
import { PermissionsTab } from './permissions-tab/PermissionsTab'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

type Props = {
  roleId?: string
  isEditMode?: boolean
}

export const RoleDetails = (props: Props): JSX.Element => {
  const { roleId, isEditMode = false } = props
  const tRoles = useTranslations('roles')
  const router = useRouter()
  const { setSubTabValueParam, subTabValue, tabValue } = useQueryParams()
  const [selectedRole, setSelectedRole] = useState<RoleResponse | undefined>(undefined)
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [hasPermissionsTabBeenSaved, setHasPermissionsTabBeenSaved] = useState<boolean>(false)

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  const getRole = useCallback(async (roleId: RoleResponse['id']) => {
    setIsLoading(true)

    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'GET',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })
      const data = await response.json()
      setSelectedRole(data)

      setIsLoading(false)
    } catch (error) {
      console.error('Error fetching role:', error)
    }
  }, [])

  useEffect(() => {
    if (roleId) {
      getRole(roleId)
    }
  }, [roleId, getRole])

  useEffect(() => {
    if (selectedRole) {
      form.setValue('name', selectedRole.name)
      form.setValue('description', selectedRole.description || '')
    }
  }, [selectedRole, form])

  const updateRole = async (roleId: RoleResponse['id'], updatedRoleData: RoleUpdate) => {
    setIsLoading(true)

    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'PUT',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updatedRoleData),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      const data = await response.json()
      console.log('Erfolgreich aktualisiert:', data)
      setHasPermissionsTabBeenSaved(true)
      setSelectedRole(data)

      setIsLoading(false)
    } catch (error) {
      console.error('Fehler:', error)
    }
  }

  const postRole = async (roleInput: RoleInput): Promise<void> => {
    try {
      const response = await fetch(`${URL}/roles`, {
        method: 'POST',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(roleInput),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      const data = await response.json()
      console.log('Erfolgreich erstellt:', data)

      router.push(`/roles/${data.id}?_tab=${tabValue}`)
    } catch (error) {
      console.error('Fehler:', error)
    }
  }

  const createRole = (values: FormRole) => {
    postRole({
      type: (tabValue as RoleType) || (DEFAULT_TAB as RoleType),
      tenant: 'ExampleCorp', // Placeholder tenant
      users: [],
      permissions: [],
      groups: [],
      createdAt: new Date().toISOString(), // Placeholder createdAt, later set by backend
      ...values,
    })
  }

  const deleteRole = async (roleId: RoleResponse['id']) => {
    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'DELETE',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      router.push('/roles')
    } catch (error) {
      console.error('Error deleting role:', error)
    }
  }

  const onSubmit = (values: FormRole): void => {
    if (roleId && selectedRole) {
      updateRole(roleId, { ...selectedRole, ...values })
    } else {
      createRole(values)
    }
  }

  const updatePermissions = (permissionIds: string[]): void => {
    if (!selectedRole || !roleId) return

    updateRole(roleId, { ...selectedRole, permissions: permissionIds })
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
  const isDefaultRole = selectedRole?.roleOrigin === ROLE_ORIGINS.DEFAULT

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(defaultSubTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultSubTab])

  const roleType = selectedRole?.type || tabValue
  const badgeTitle = roleType ? tRoles(`${roleType}Roles`).slice(0, -1) : undefined

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={roleId ? selectedRole?.name : tRoles('newRole')}
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
            deleteRole={() => roleId && deleteRole(roleId)}
          />
        )}

        {subTabValue === subTabValues.permissions.value && (
          <PermissionsTab
            updatePermissions={updatePermissions}
            currentSelectedPermissionIds={selectedRole?.permissions || []}
            roleType={tabValue}
            hasPermissionsTabBeenSaved={hasPermissionsTabBeenSaved}
            setHasPermissionsTabBeenSaved={setHasPermissionsTabBeenSaved}
            isDefaultRole={isDefaultRole}
          />
        )}

        {subTabValue === subTabValues.groupAssignment.value && (
          <GroupAssignmentTab groupIds={selectedRole?.groups || []} />
        )}
      </PageBackground>
    </PageContainer>
  )
}
