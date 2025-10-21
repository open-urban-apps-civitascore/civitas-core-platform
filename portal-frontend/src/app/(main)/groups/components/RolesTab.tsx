'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { Group, GroupTabProps } from '@/types/groups'

import { updateGroup } from '../actions'
import { RoleCategory } from './RoleCategory'

interface Role {
  id: string
  name: string
  description: string
  type: 'System' | 'Data' | 'Governance'
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const RolesTab = (props: GroupTabProps) => {
  const { groupData } = props
  const t = useTranslations('groups')
  const tRoles = useTranslations('roles')
  const originalRoles = groupData.roles
  const router = useRouter()
  const [group, setGroup] = useState<Group>(groupData)

  const [allRoles, setAllRoles] = useState<Role[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const hasChanges = useMemo(
    () => JSON.stringify(group.roles.sort()) !== JSON.stringify(originalRoles.sort()),
    [group, originalRoles],
  )

  // Fetch all roles
  useEffect(() => {
    const fetchData = async () => {
      try {
        const rolesResponse = await fetch(`${URL}/roles`)

        if (!rolesResponse.ok) {
          throw new Error('Failed to fetch data')
        }

        const rolesData = await rolesResponse.json()

        setAllRoles(rolesData)
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setIsLoading(false)
      }
    }
    fetchData()
  }, [])

  const handleAddRole = (roleId: string) => {
    const updatedRoles = [...group.roles, roleId]
    setGroup({ ...group, roles: updatedRoles })
  }

  const handleRemoveRole = (roleId: string) => {
    const updatedRoles = group.roles.filter(id => id !== roleId)
    setGroup({ ...group, roles: updatedRoles })
  }

  const handleUpdateGroup = async () => {
    try {
      setIsLoading(true)
      await updateGroup(group)
      router.refresh()
    } catch (error) {
      console.error('An error occurred while updating the group: ', error)
    } finally {
      setIsLoading(false)
    }
  }

  if (isLoading) {
    return <LoadingSpinner className="h-full" />
  }

  if (!group) {
    return (
      <div className="p-8">
        <p>{t('roles.userNotFound')}</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col justify-between h-full">
      <ContentCard>
        <DetailsFieldContainer isTitleField>
          <h2>{t('roles.groupInfo')}</h2>
        </DetailsFieldContainer>
        <RoleCategory
          title={tRoles('systemRoles')}
          category="System"
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={allRoles}
        />

        <RoleCategory
          title={tRoles('dataRoles')}
          category="Data"
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={allRoles}
        />

        <RoleCategory
          title={tRoles('governanceRoles')}
          category="Governance"
          onAddRole={handleAddRole}
          onRemoveRole={handleRemoveRole}
          groupRoles={group.roles}
          allRoles={allRoles}
          className="border-0"
        />
      </ContentCard>
      <ActionButtons
        onCancelClick={() => setGroup({ ...group, roles: originalRoles })}
        confirmButtonType="button"
        isConfirmButtonDisabled={!hasChanges}
        isCancelButtonDisabled={!hasChanges}
        onConfirmClick={handleUpdateGroup}
      />
    </div>
  )
}
