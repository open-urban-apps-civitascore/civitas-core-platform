import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Group, UserGroupsListData } from '@/types/groups'
import { RoleResponse } from '@/types/roles'

import GroupsTable from './GroupsTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const mapRolesData = (roles: RoleResponse[]): { id: string; title: string }[] =>
  roles.map(role => ({ id: role.id, title: role.name }))
interface GroupsTabProps {
  userId: string
}

const transformGroupsToListData = (groups: Group[], roles: RoleResponse[], userId: string): UserGroupsListData[] =>
  groups.map(group => ({
    id: group.id,
    title: group.title,
    description: group.description,
    roles: group.roles.flatMap(groupRole => {
      const matchingRole = roles.find(role => role.id === groupRole)
      return matchingRole ? matchingRole.name : []
    }),
    memberSince: group.users.find(user => user.id === userId)?.assignedAt || '',
    contact: group.contact,
  }))

type UserRole = { id: string; title: string }

export const GroupsTab = (props: GroupsTabProps) => {
  const { userId } = props
  const t = useTranslations('users')
  const [groups, setGroups] = useState<UserGroupsListData[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [searchString, setSearchString] = useState('')
  const [filteredGroups, setFilteredGroups] = useState(groups)
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const rowCount = groups.length
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  useEffect(() => {
    // this implementation has te be adjusted once the backend is ready
    const fetchGroups = async () => {
      try {
        const [groupsResponse, rolesResponse] = await Promise.all([
          fetch(`${URL}/groups`, {
            cache: 'no-store',
          }),
          fetch(`${URL}/roles`, {
            cache: 'no-store',
          }),
        ])

        if (!groupsResponse.ok || !rolesResponse.ok) {
          throw new Error('Failed to fetch data')
        }

        const groupsData: Group[] = await groupsResponse.json()
        const rolesData: RoleResponse[] = await rolesResponse.json()

        const userGroups = groupsData.filter(group => group.users.filter(user => user.id === userId).length > 0)
        setGroups(transformGroupsToListData(userGroups, rolesData, userId))
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setIsLoading(false)
      }
    }
    fetchGroups()
  }, [userId])

  useEffect(() => {
    if (searchString) {
      setFilteredGroups(
        groups.filter(
          group =>
            group.title.toLowerCase().includes(searchString.toLowerCase()) ||
            group.contact?.displayName.toLowerCase().includes(searchString.toLowerCase()) ||
            group.description.toLowerCase().includes(searchString.toLowerCase()),
        ),
      )
    } else {
      setFilteredGroups(groups)
    }
  }, [searchString, groups])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }
  return (
    <div className="h-full">
      <h2>{t('groupsTab.title')}</h2>
      <SearchHeader searchString={searchString} onChangeSearchString={setSearchString} />
      <TableContainer>
        <GroupsTable
          groups={filteredGroups}
          rowCount={rowCount}
          pageIndex={pageIndex}
          pageSize={pageSize}
          onPaginationChange={handlePagination}
          sorting={sorting}
          onSortingChange={setSorting}
          totalPages={totalPages}
          isLoading={isLoading}
        />
      </TableContainer>
    </div>
  )
}
