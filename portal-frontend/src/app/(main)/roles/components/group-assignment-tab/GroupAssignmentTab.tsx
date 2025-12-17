'use client'

import { PaginationState, Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { Button } from '@/components/ui/button'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { flattenGroups } from '@/utils/groups'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import { GroupTable } from './GroupTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

type GroupAssignmentTabProps = {
  groupIds: Group['id'][]
  onGroupAssignmentUpdate: (newGroupIds: string[]) => void
  roleName: Role['name']
}

export const GroupAssignmentTab = (props: GroupAssignmentTabProps) => {
  const { groupIds, onGroupAssignmentUpdate, roleName } = props
  const t = useTranslations('roles.groupAssignmentTab')
  const router = useRouter()
  const [groups, setGroups] = useState<Group[]>([])
  const [filteredGroups, setFilteredGroups] = useState<Group[]>([])
  const [assignedGroupIds, setAssignedGroupIds] = useState<Group['id'][]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [searchInput, setSearchInput] = useState<string>('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'title', desc: false }])
  const [totalPages, setTotalPages] = useState<number>(1)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [groupSelection, setGroupSelection] = useState<RowSelectionState>({})
  const [originalGroupSelection, setOriginalGroupSelection] = useState<RowSelectionState>({})
  const rowCount = groups.length

  // currently there is no API endpoint to get groups and subgroups by Ids, so we need to filter and flatten them on the client side
  // that's why pagination is not working correctly when there are subgroups assigned to the role
  const getGroups = useCallback(async () => {
    try {
      const response = await fetch(`${URL}/groups?_limit=${pageSize}&_page=${pageIndex + 1}`, {
        cache: 'no-store',
      })

      if (!response.ok) {
        throw new Error('An error occurred while loading group data')
      }

      const data: Group[] = await response.json()

      const flattenedData = flattenGroups(data)

      const filteredGroups = flattenedData.filter(group => assignedGroupIds.includes(group.id))
      setGroups(filteredGroups)

      const totalFilteredItems = assignedGroupIds.length
      setTotalPages(Math.ceil(totalFilteredItems / pageSize))
    } catch (error) {
      console.error('Error fetching groups:', error)
    } finally {
      setIsLoading(false)
    }
  }, [pageIndex, pageSize, assignedGroupIds])

  useEffect(() => {
    setAssignedGroupIds(groupIds)
  }, [groupIds])

  useEffect(() => {
    if (assignedGroupIds.length > 0) {
      getGroups()
    } else {
      setIsLoading(false)
    }
  }, [getGroups, assignedGroupIds])

  useEffect(() => {
    const originalGroupSelection = assignedGroupIds.reduce((acc, groupId) => ({ ...acc, [groupId]: true }), {})
    setGroupSelection(originalGroupSelection)
    setOriginalGroupSelection(originalGroupSelection)
  }, [assignedGroupIds])

  useEffect(() => {
    if (searchInput) {
      setFilteredGroups(
        groups.filter(
          group =>
            group.title.toLowerCase().includes(searchInput.toLowerCase()) ||
            group.contact?.displayName.toLowerCase().includes(searchInput.toLowerCase()) ||
            group.description.toLowerCase().includes(searchInput.toLowerCase()),
        ),
      )
    } else {
      setFilteredGroups(groups)
    }
  }, [searchInput, groups])

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

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!isLoading && assignedGroupIds.length === 0 && filteredGroups.length === 0) {
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
        isLoading={isLoading}
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
