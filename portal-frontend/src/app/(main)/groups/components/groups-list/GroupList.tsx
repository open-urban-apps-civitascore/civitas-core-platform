'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group } from '@/types/groups'
import { GroupsTable } from './GroupsTable'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'

export const getSortParam = (sorting: SortingState) => {
  if (sorting.length > 0) {
    const sortingId = sorting[0]?.id
    const sortParam = `&_sort=${sortingId}`
    const orderParam = sorting[0]?.desc ? `&_order=desc` : `&_order=asc`
    return `${sortParam}${orderParam}`
  }
  return ''
}

interface GroupsListProps {
  groupsData: Group[]
  totalCount: number
}

const GroupsList = (props: GroupsListProps) => {
  const { groupsData, totalCount } = props
  const t = useTranslations('groups')
  const router = useRouter()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [isWarningModalOpen, setIsWarningmodalOpen] = useState(false)
  const [isInfoModalOpen, setIsInfoModalOpen] = useState(false)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    setTotalPages,
    pageIndex,
    pageSize,
    sorting,
    search,
    totalPages,
  } = useQueryParams()

  const rowCount = totalCount || 0
  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize, setTotalPages])

  const handleRowClick = (row: Row<Group>) => {
    const params = getApiRequestParamsByUrl()
    router.push(`groups/${row.id}?${params}`)
  }

  const handleDeleteGroup = () => {
    
  }

  const handleDeleteGroupClick = (groupId: string) => {
    const group = groupsData.find(group => group.id === groupId)
    if (!group?.members) return
    if (group?.members.length > 0) {
      setIsInfoModalOpen(true)
    } else setIsWarningmodalOpen(true)
  }

  const CustomElement = () => {
    const params = getApiRequestParamsByUrl()
    return (
      <Button onClick={() => router.push(`groups/create?${params}`)}>
        <Plus />
        {t('newGroup')}
      </Button>
    )
  }

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader
          customElement={<CustomElement />}
          onChangeSearchString={setSearchParam}
          searchString={search}
          placeholder={t('search')}
        />
        <TableContainer>
          <GroupsTable
            groups={groupsData}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onRowClick={handleRowClick}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
            onDeleteGroupClick={() => {}}
          />
        </TableContainer>
      </PageBackground>
      <WarningModal title={t('WarningModal.title')} description={t('WarningModal.description')} />
    </PageContainer>
  )
}

export default GroupsList
