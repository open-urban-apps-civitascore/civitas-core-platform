import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Group, UserGroupsListData } from '@/types/groups'
import { Role } from '@/types/roles'

import GroupsTable from './GroupsTable'

interface GroupsTabProps {
  userId: string
}

const transformGroupsToListData = (groups: Group[], roles: Role[], userId: string): UserGroupsListData[] =>
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

export const GroupsTab = (props: GroupsTabProps) => {
  const { userId } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const [searchString, setSearchString] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'title', desc: false }])

  const { data: groupsData, isFetching: isLoadingGroups, error: groupsError } = useGetGroups()
  const { data: rolesData, isFetching: isLoadingRoles, error: rolesError } = useGetRoles()

  const groups = useMemo(() => {
    if (groupsData && rolesData && userId) {
      const userGroups = groupsData?.data.filter(group => group.users.filter(user => user.id === userId).length > 0)
      return transformGroupsToListData(userGroups, rolesData.data, userId)
    } else {
      return []
    }
  }, [groupsData, rolesData, userId])

  const filteredGroups = useMemo(() => {
    if (searchString) {
      return groups.filter(
        group =>
          group.title.toLowerCase().includes(searchString.toLowerCase()) ||
          group.contact?.displayName.toLowerCase().includes(searchString.toLowerCase()) ||
          group.description.toLowerCase().includes(searchString.toLowerCase()),
      )
    } else {
      return groups
    }
  }, [groups, searchString])

  const rowCount = groups.length
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  const isLoading = isLoadingGroups || isLoadingRoles
  const error = groupsError || rolesError

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const CustomElement = <Button>{t('groupsTab.addGroup')}</Button>
  return (
    <ContentCard className={cn(!error && !isLoading ? 'h-full' : 'h-50')}>
      {!error && !isLoading && (
        <>
          <SubHeader title={t('groupsTab.title')} customElement={CustomElement} />
          <SearchHeader searchString={searchString} onChangeSearchString={setSearchString} className="my-2" />
          <TableContainer className="[--search-height:calc(--spacing(30))]">
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
        </>
      )}
      {isLoading && <LoadingSpinner className="h-full" />}
      {error && <p className="h-full flex items-center justify-center">{tCommon('errors.loadingError')}</p>}
    </ContentCard>
  )
}
