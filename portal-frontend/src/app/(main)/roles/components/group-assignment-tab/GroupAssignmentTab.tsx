'use client'

import { PaginationState, Row, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { Button } from '@/components/ui/button'
import { Group } from '@/types/groups'

import { GroupTable } from './GroupTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

type GroupAssignmentTabProps = {
  groupIds: Group['id'][]
}

export const GroupAssignmentTab = (props: GroupAssignmentTabProps) => {
  const { groupIds } = props
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
  const rowCount = groups.length

  const getGroups = useCallback(async () => {
    try {
      const response = await fetch(`${URL}/groups?_limit=${pageSize}&_page=${pageIndex + 1}`, {
        cache: 'no-store',
      })

      if (!response.ok) {
        throw new Error('An error occurred while loading group data')
      }

      const data: Group[] = await response.json()
      const filteredGroups = data.filter(group => assignedGroupIds.includes(group.id))
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
    <Button>
      <Plus /> {t('addGroup')}
    </Button>
  )

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!isLoading && assignedGroupIds.length === 0 && filteredGroups.length === 0) {
    return <NoDataPage title={t('noGroupsAssigned')} buttonText={t('addGroup')} />
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
    </>
  )
}
