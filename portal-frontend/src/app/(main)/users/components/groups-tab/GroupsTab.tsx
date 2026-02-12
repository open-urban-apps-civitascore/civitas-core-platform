import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { User } from '@/types/users'
import { mapGroupsApiToListData } from '@/utils/groups'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import GroupsTable from './GroupsTable'

interface GroupsTabProps {
  user: User
}

export const GroupsTab = (props: GroupsTabProps) => {
  const { user } = props
  const userId = user.id
  const groupIds = user.groups
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const [searchString, setSearchString] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'title', desc: false }])
  const [isGroupAssignmentModalOpen, setIsGroupAssignmentModalOpen] = useState(false)

  const getGroupsRequestParams = () => {
    const params = new URLSearchParams()
    groupIds.forEach(group => {
      params.append('id', group)
    })
    return params
  }

  const {
    data: groupsData,
    isFetching: isLoadingGroups,
    error: groupsError,
  } = useGetGroups({ isEnabled: groupIds.length > 0, params: getGroupsRequestParams() })

  const groups = useMemo(() => {
    if (groupsData && userId) {
      const userGroups = groupsData?.data.filter(
        group => group?.members && group.members.filter(user => user?.id === userId).length > 0,
      )
      return mapGroupsApiToListData(userGroups)
    } else {
      return []
    }
  }, [groupsData, userId])

  const filteredGroups = useMemo(() => {
    if (searchString) {
      return groups.filter(
        group =>
          group.name.toLowerCase().includes(searchString.toLowerCase()) ||
          group.contactUser?.name.toLowerCase().includes(searchString.toLowerCase()) ||
          group.description.toLowerCase().includes(searchString.toLowerCase()),
      )
    } else {
      return groups
    }
  }, [groups, searchString])

  const rowCount = groups.length
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  const isLoading = isLoadingGroups
  const error = groupsError

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const handleDelete = (id: string) => {}

  const handleUpdateGroups = () => {}

  const CustomElement = <Button onClick={() => setIsGroupAssignmentModalOpen(true)}>{t('groupsTab.addGroup')}</Button>
  return (
    <PageBackground>
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
                onDelete={handleDelete}
              />
            </TableContainer>
          </>
        )}
        <GroupAssignmentModal
          open={isGroupAssignmentModalOpen}
          userName={`${user.firstName} ${user.lastName}`}
          originalGroups={groupIds}
          onUpdateGroups={handleUpdateGroups}
        />
        {isLoading && <LoadingSpinner className="h-full" />}
        {error && <p className="h-full flex items-center justify-center">{tCommon('errors.loadingError')}</p>}
      </ContentCard>
    </PageBackground>
  )
}
