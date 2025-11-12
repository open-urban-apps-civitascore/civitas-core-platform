'use client'

import { Row, RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { isPageIndexHigherThanTotalPages } from '@/utils/table'

import { DataSpace } from '../../../../types/dataspaces'
import { DataSpacesTable } from './components/DataSpacesTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const DataSpacesPage = () => {
  const t = useTranslations('dataspaces')
  const router = useRouter()

  const [listDataSpaces, setListDataSpaces] = useState<DataSpace[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [rowCount, setRowCount] = useState(0)
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  useEffect(() => {
    if (isPageIndexHigherThanTotalPages(pageIndex, totalPages)) {
      setPaginationParams({ pageIndex: totalPages - 1, pageSize: pageSize })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [totalPages, pageIndex, pageSize])

  const getDataSpaces = useCallback(async () => {
    const params = getApiRequestParamsByUrl()

    try {
      setIsLoading(true)

      const dataSpacesResponse = await fetch(`${URL}/dataspaces?${params.toString()}`, {
        cache: 'no-store',
      })

      const dataSpacesData: DataSpace[] = await dataSpacesResponse.json()
      setListDataSpaces(dataSpacesData)

      const totalCount = Number(dataSpacesResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }

      setIsLoading(false)
    } catch (error) {
      console.error(error)
      setIsLoading(false)
    }
  }, [getApiRequestParamsByUrl, rowCount])

  useEffect(() => {
    getDataSpaces()
  }, [getDataSpaces, sorting, rowCount, pageIndex, pageSize])

  const handleRowClick = (row: Row<DataSpace>) => {
    if (row.id) {
      const params = getApiRequestParamsByUrl()
      router.push(`/dataspaces/${row.original.id}?${params}`, {})
    }
  }

  const CustomElement = (
    <Button onClick={() => router.push('/dataspaces/create')}>
      <Plus />
      {t('newDataSpace')}
    </Button>
  )

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader searchString={search} onChangeSearchString={setSearchParam} customElement={CustomElement} />
        <TableContainer>
          <DataSpacesTable
            dataspaces={listDataSpaces}
            isLoading={isLoading}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            sorting={sorting}
            onRowClick={handleRowClick}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default DataSpacesPage
