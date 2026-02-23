'use client'

import { PaginationState, Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { Button } from '@/components/ui/button'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import { GroupTable } from './GroupTable'

interface GroupAssignmentTabProps {
  assignedGroupIds: Group['id'][]
  onGroupAssignmentUpdate: (newGroupIds: string[]) => void
  roleName: Role['name']
}

const getGroupSelection = (groupIds: Group['id'][]) =>
  groupIds.reduce((acc, groupId) => ({ ...acc, [groupId]: true }), {})

export const GroupAssignmentTab = (props: GroupAssignmentTabProps) => {
  const { assignedGroupIds, onGroupAssignmentUpdate, roleName } = props
  const t = useTranslations('roles.groupAssignmentTab')
  const router = useRouter()
  const [searchInput, setSearchInput] = useState<string>('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'title', desc: false }])
  const [totalPages, setTotalPages] = useState<number>(1)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [groupSelection, setGroupSelection] = useState<RowSelectionState>(getGroupSelection(assignedGroupIds))
  const originalGroupSelection = getGroupSelection(assignedGroupIds)

  const requestParams = new URLSearchParams(`_limit=${pageSize}&_page=${pageIndex + 1}`)
  const { data: groupsData, isFetching } = useGetGroups({ params: requestParams })

  const groups = useMemo(() => {
    if (!groupsData?.data) {
      return []
    }
    // currently there is no API endpoint to get groups and subgroups by Ids, so we need to filter and flatten them on the client side
    // that's why pagination is not working correctly when there are subgroups assigned to the role
    // const flattenedGroups = flattenGroups(groupsData?.data)
    const filteredGroups = groupsData.data.filter(group => assignedGroupIds.includes(group.id))
    return filteredGroups
  }, [groupsData?.data, assignedGroupIds])

  const filteredGroups = useMemo(() => {
    if (!searchInput) {
      return groups
    }
    return groups.filter(
      group =>
        group.name.toLowerCase().includes(searchInput.toLowerCase()) ||
        group.contactUser?.name.toLowerCase().includes(searchInput.toLowerCase()) ||
        group.description.toLowerCase().includes(searchInput.toLowerCase()),
    )
  }, [searchInput, groups])

  const rowCount = groups.length

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const onRowClick = (row: Row<Group>) => {
    router.push(`/groups/${row.original.id}`)
  }

  const customElement = (
    <Button onClick={() => setIsModalOpen(true)}>
      <Plus /> {t('addGroup')}
    </Button>
  )

  const haveGroupsBeenTouched =
    Object.keys(groupSelection).every(key => assignedGroupIds.includes(key)) === false ||
    assignedGroupIds.every(id => Object.keys(groupSelection).includes(id)) === false

  if (isFetching) {
    return <LoadingSpinner />
  }

  if (!isFetching && assignedGroupIds.length === 0 && filteredGroups.length === 0) {
    return (
      <>
        <NoDataPage
          title={t('noGroupsAssigned')}
          buttonText={t('addGroup')}
          onButtonClick={() => setIsModalOpen(true)}
        />

        <GroupAssignmentModal
          open={isModalOpen}
          onOpenChange={setIsModalOpen}
          selection={groupSelection}
          setSelection={setGroupSelection}
          originalSelection={originalGroupSelection}
          onGroupAssignmentUpdate={onGroupAssignmentUpdate}
          haveGroupsBeenTouched={haveGroupsBeenTouched}
          roleName={roleName}
        />
      </>
    )
  }

  return (
    <>
      <SearchHeader searchString={searchInput} onChangeSearchString={setSearchInput} customElement={customElement} />
      <GroupTable
        groups={filteredGroups}
        isLoading={isFetching}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        onPaginationChange={handlePagination}
        sorting={sorting}
        onSortingChange={setSorting}
        totalPages={totalPages}
        onRowClick={onRowClick}
      />

      <GroupAssignmentModal
        open={isModalOpen}
        onOpenChange={setIsModalOpen}
        selection={groupSelection}
        setSelection={setGroupSelection}
        originalSelection={originalGroupSelection}
        onGroupAssignmentUpdate={onGroupAssignmentUpdate}
        haveGroupsBeenTouched={haveGroupsBeenTouched}
        roleName={roleName}
      />
    </>
  )
}
