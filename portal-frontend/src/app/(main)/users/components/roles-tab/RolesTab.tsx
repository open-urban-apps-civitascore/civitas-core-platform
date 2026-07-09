'use client'

import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetAssignments } from '@/app/services/api/assignments/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { AlertBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'

import { RolesAssignmentTable } from './RolesAssignmentTable'

type ScopeSegment = 'platformWide' | 'dataset' | 'datasource' | 'datastructure' | 'datapool'

const SCOPE_SEGMENT_PARAMS: Record<ScopeSegment, string[][]> = {
  platformWide: [
    ['scopeType', ASSIGNMENT_SCOPE_TYPES.TENANT],
    ['roleType', 'SYSTEM'],
  ],
  dataset: [['scopeType', ASSIGNMENT_SCOPE_TYPES.DATASET]],
  datasource: [['scopeType', ASSIGNMENT_SCOPE_TYPES.DATASOURCE]],
  datastructure: [['scopeType', ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE]],
  datapool: [['scopeType', ASSIGNMENT_SCOPE_TYPES.DATAPOOL]],
}

interface RolesTabProps {
  userId: string
}

export const RolesTab = (props: RolesTabProps) => {
  const { userId } = props
  const t = useTranslations()

  const [activeSegment, setActiveSegment] = useState<ScopeSegment>('platformWide')
  const [searchString, setSearchString] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'role.name', desc: false }])

  const segments: { value: ScopeSegment; label: string }[] = [
    { value: 'platformWide', label: t('users.rolesTab.segments.platformWide') },
    { value: 'datapool', label: t('users.rolesTab.segments.datapools') },
    { value: 'dataset', label: t('users.rolesTab.segments.datasets') },
    { value: 'datasource', label: t('users.rolesTab.segments.datasources') },
    { value: 'datastructure', label: t('users.rolesTab.segments.datastructures') },
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

  const {
    data: assignmentsData,
    isFetching: isLoading,
    error,
  } = useGetAssignments({
    params: requestParams,
    isEnabled: !!userId,
  })

  const assignments = assignmentsData?.data ?? []
  const rowCount = assignmentsData?.totalElements ?? 0
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  const getScopedInfoBannerText = (): string | null => {
    if (activeSegment === 'dataset') return t('users.rolesTab.scopedInfoBanner.dataset')
    if (activeSegment === 'datasource') return t('users.rolesTab.scopedInfoBanner.datasource')
    if (activeSegment === 'datastructure') return t('users.rolesTab.scopedInfoBanner.datastructure')
    if (activeSegment === 'datapool') return t('users.rolesTab.scopedInfoBanner.datapool')
    return null
  }

  const infoBannerText = getScopedInfoBannerText()

  const handleSegmentChange = (segment: ScopeSegment) => {
    setActiveSegment(segment)
    setPageIndex(0)
    setSearchString('')
  }

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  if (error) {
    return (
      <ContentCard className="h-50">
        <p className="h-full flex items-center justify-center">{t('common.errors.loadingError')}</p>
      </ContentCard>
    )
  }
  return (
    <>
      <SearchHeader
        searchString={searchString}
        onChangeSearchString={value => {
          setSearchString(value)
          setPageIndex(0)
        }}
        className="my-2"
      />
      <div className="flex items-center justify-between gap-4 mb-4">
        <div className="flex gap-2" role="tablist">
          {segments.map(seg => (
            <Button
              key={seg.value}
              type="button"
              role="tab"
              aria-selected={activeSegment === seg.value}
              variant={activeSegment === seg.value ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSegmentChange(seg.value)}
            >
              {seg.label}
            </Button>
          ))}
        </div>
        {infoBannerText && <AlertBox text={infoBannerText} />}
      </div>
      <TableContainer shouldRespectSearchHeight={true} shouldRespectSegmentedControlBar>
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
  )
}
