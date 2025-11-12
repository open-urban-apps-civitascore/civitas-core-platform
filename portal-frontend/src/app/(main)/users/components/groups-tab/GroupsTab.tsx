import { ContentCard } from '@/components/content-card/ContentCard'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { Group, UserGroupsListData } from '@/types/groups'
import { useTranslations } from 'next-intl'
import React, { useEffect, useMemo, useState } from 'react'
import GroupsTable from './GroupsTable'
import { PaginationState, SortingState } from '@tanstack/react-table'
import { pages } from 'next/dist/build/templates/app-page'

interface GroupsTabProps {
  userId: string
}

const transformGroupsToListData = (groups: Group[], userId: string): UserGroupsListData[] =>
  groups.map(group => ({
    id: group.id,
    title: group.title,
    description: group.description,
    roles: group.roles,
    memberSince: group.users.find(user => user.id === userId)?.assignedAt || '',
    contact: group.contact,
  }))

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
        const groupsResponse = await fetch(`${URL}/groups`)

        if (!groupsResponse.ok) {
          throw new Error('Failed to fetch data')
        }

        const groupsData: Group[] = await groupsResponse.json()
        const userGroups = groupsData.filter(group => group.users.filter(user => user.id === userId))
        setGroups(transformGroupsToListData(userGroups, userId))
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setIsLoading(false)
      }
    }
    fetchGroups()
  }, [userId])

  const handleSearchStringChange = (value: string) => {
    if (value) {
      setFilteredGroups(
        groups.filter(
          group =>
            group.title.toLowerCase().includes(value.toLowerCase()) ||
            group.contact?.displayName.toLowerCase().includes(value.toLowerCase()) ||
            group.description.toLowerCase().includes(value.toLowerCase()),
        ),
      )
    } else {
      setFilteredGroups(groups)
    }
  }

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }
  return (
    <ContentCard>
      <h2>{t('groupsTab.title')}</h2>
      <SearchHeader searchString="" onChangeSearchString={handleSearchStringChange} />
      <GroupsTable
        groups={filteredGroups}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        onPaginationChange={handlePagination}
        sorting={sorting}
        onSortingChange={setSorting}
        totalPages={totalPages}
      />
    </ContentCard>
  )
}
