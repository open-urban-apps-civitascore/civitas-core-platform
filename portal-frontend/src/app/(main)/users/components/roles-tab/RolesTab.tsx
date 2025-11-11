'use client'

import { Loader2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Group } from '@/types/groups'
import { BaseRole, ROLE_TYPES, UserRolesTableData } from '@/types/roles'

import { RoleCategory } from './RoleCategory'

interface RolesTabProps {
  groupIds: string[]
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const RolesTab = (props: RolesTabProps) => {
  const { groupIds } = props
  const t = useTranslations('users')
  const tRoles = useTranslations('roles')
  const [roles, setRoles] = useState<UserRolesTableData[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const mapRolesData = (roles: BaseRole[], groupData: Group[]) => {
    const allRoles = groupData.flatMap(group =>
      group.roles.flatMap(groupRole => {
        const currentRole = roles.find(role => role.id === groupRole)
        if (!currentRole) return []
        return {
          id: currentRole.id,
          name: currentRole.name,
          inherited: false,
          group: group?.title || null,
          dataspace: group?.dataspace || null,
          type: currentRole.type,
        }
      }),
    )
    return allRoles
  }

  useEffect(() => {
    const fetchData = async () => {
      try {
        const groupIdParams = groupIds.map(id => `id=${id}`).join('&')
        const groupsResponse = await fetch(`${URL}/groups?${groupIdParams}`)

        if (!groupsResponse.ok) {
          throw new Error('Failed to fetch data')
        }

        const groupsData: Group[] = await groupsResponse.json()

        const roleIds = new Set(groupsData.flatMap(group => group.roles))
        if (roleIds.size > 0) {
          const roleIdParams = [...roleIds].map(role => `id=${role}`).join('&')
          const rolesResponse = await fetch(`${URL}/roles?${roleIdParams}`)
          if (!rolesResponse.ok) {
            throw new Error('Failed to fetch data')
          }
          const rolesData = mapRolesData(await rolesResponse.json(), groupsData)
          setRoles(rolesData)
        }
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setIsLoading(false)
      }
    }

    fetchData()
  }, [groupIds])

  return (
    <ContentCard>
      <DetailsFieldContainer isTitleField>
        <h2>{t('roles.title')}</h2>
      </DetailsFieldContainer>
      <RoleCategory
        title={tRoles('systemRoles')}
        rolesType={ROLE_TYPES.SYSTEM}
        roles={roles.filter(role => role.type === ROLE_TYPES.SYSTEM)}
        isLoading={isLoading}
      />

      <RoleCategory
        title={tRoles('dataRoles')}
        rolesType={ROLE_TYPES.DATA}
        roles={roles.filter(role => role.type === ROLE_TYPES.DATA)}
        isLoading={isLoading}
      />

      <RoleCategory
        title={tRoles('governanceRoles')}
        rolesType={ROLE_TYPES.GOVERNANCE}
        roles={roles.filter(role => role.type === ROLE_TYPES.GOVERNANCE)}
        isLoading={isLoading}
      />
    </ContentCard>
  )
}
