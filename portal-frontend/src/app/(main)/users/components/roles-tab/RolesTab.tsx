'use client'

import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetAssignments } from '@/app/services/api/assignments/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { SegmentedControlBar, Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { TableContainer } from '@/components/table-container/TableContainer'

import { RolesAssignmentTable } from './RolesAssignmentTable'

type ScopeSegment = 'platformWide' | 'dataset' | 'datasource' | 'datastructure'

const SCOPE_SEGMENT_PARAMS: Record<ScopeSegment, string[][]> = {
  platformWide: [
    ['scopeType', 'TENANT'],
    ['roleType', 'SYSTEM'],
  ],
  dataset: [['scopeType', 'DATASET']],
  datasource: [['scopeType', 'DATASOURCE']],
  datastructure: [['scopeType', 'DATASTRUCTURE']],
}

interface RolesTabProps {
  userId: string
  isReadOnly?: boolean
}

export const RolesTab = (props: RolesTabProps) => {
  const { userId, isReadOnly = true } = props
  const t = useTranslations()

  const [activeSegment, setActiveSegment] = useState<ScopeSegment>('platformWide')
  const [searchString, setSearchString] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'role.name', desc: false }])

  const segments: Tab<ScopeSegment>[] = [
    { value: 'platformWide', label: 'users.rolesTab.segments.platformWide' },
    { value: 'dataset', label: 'users.rolesTab.segments.datasets' },
    { value: 'datasource', label: 'users.rolesTab.segments.datasources' },
    { value: 'datastructure', label: 'users.rolesTab.segments.datastructures' },
  ]

  const requestParams = useMemo(() => {
    const params = new URLSearchParams(SCOPE_SEGMENT_PARAMS[activeSegment])
    params.set('userId', userId)
    params.set('page', String(pageIndex))
    params.set('size', String(pageSize))
    if (searchString) {
      params.set('q', searchString)
    }
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    return params
  }, [activeSegment, userId, pageIndex, pageSize, searchString, sorting])

  const { data: assignmentsData, isFetching: isLoading, error } = useGetAssignments({ params: requestParams })

  const assignments = assignmentsData?.data ?? []
  const rowCount = assignmentsData?.totalElements ?? 0
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  const handleSegmentChange = (segment: ScopeSegment) => {
    setActiveSegment(segment)
    setPageIndex(0)
    setSearchString('')
  }

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  return (
    <PageBackground hasBackground={!isReadOnly}>
      {error ? (
        <ContentCard className="h-50">
          <p className="h-full flex items-center justify-center">{t('common.errors.loadingError')}</p>
        </ContentCard>
      ) : (
        <>
          <SearchHeader
            searchString={searchString}
            onChangeSearchString={value => {
              setSearchString(value)
              setPageIndex(0)
            }}
            className="my-2"
          />
          <SegmentedControlBar
            tabs={segments}
            selectedTab={activeSegment}
            onTabChange={handleSegmentChange}
            className="mb-4"
          />
          <TableContainer shouldRespectSearchHeight={false} className="h-[calc(100%-7.5rem)]">
            <RolesAssignmentTable
              assignments={assignments}
              rowCount={rowCount}
              pageIndex={pageIndex}
              pageSize={pageSize}
              sorting={sorting}
              totalPages={totalPages}
              isLoading={isLoading}
              isPlatformWide={activeSegment === 'platformWide'}
              onPaginationChange={handlePagination}
              onSortingChange={setSorting}
            />
          </TableContainer>
        </>
      )}
    </PageBackground>
  )
}
