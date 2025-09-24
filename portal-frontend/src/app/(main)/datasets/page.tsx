'use client'

import { SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { TableContainer } from '@/components/table-container/TableContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'
import { Button } from '@/components/ui/button'

import DatasetsTable from './components/DatasetsTable'

export type Status = 'open' | 'closed' | null
export type Creator = { id: string; firstName: string; lastName: string }
export type Distribution = {
  format: string
  title: string
  url: string
}

export type Category = { id: string; title: string }

export type Catalog = {
  id: string
  [`dct:title`]: string
  [`dct:description`]: string
  [`dct:publisherId`]: string
  [`dcat:datasetIds`]: string[]
  [`dct:issued`]: string
  [`dct:modified`]: string
}

export type DatasetResponse = {
  id: string
  title: string
  creator: Creator[]
  issued: string
  modified: string
  status: Status
  distribution: (Distribution & { id: string }) | null
  catalog: string[]
  series: Category
  department: Category
}

export type Dataset = {
  id: string
  name: string
  dataSpace: string
  department: string
  creator: string[]
  lastUpdated: string
  status: Status
  releaseProcess: null
  distribution: Distribution | null
}

export const mapDatasets = (datasets: DatasetResponse[]): Dataset[] =>
  datasets.map(dataset => ({
    id: dataset.id,
    name: dataset.title,
    dataSpace: dataset.series.title,
    department: dataset.department.title,
    creator: dataset.creator.map(creator => `${creator.firstName} ${creator.lastName}`),
    lastUpdated: dataset.modified,
    status: dataset.status,
    releaseProcess: null,
    distribution: dataset.distribution
      ? {
          format: dataset.distribution?.format,
          title: dataset.distribution?.title,
          url: dataset.distribution?.url,
        }
      : null,
  }))

export const getSortParam = (sorting: SortingState) => {
  if (sorting.length > 0) {
    const sortingId = sorting[0]?.id === 'name' ? 'title' : 'modified'
    const sortParam = `&_sort=${sortingId}`
    const orderParam = sorting[0]?.desc ? `&_order=desc` : `&_order=asc`
    return `${sortParam}${orderParam}`
  }
  return ''
}

export const getSearchParam = (searchString: string) => (searchString ? `&q=${searchString}` : '')

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [pageIndex, setPageIndex] = useState(0)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  // adjusts the pageIndex when filtering reduces the totalPages and the user is on a page beyond that limit
  useEffect(() => {
    if (totalPages > 0 && pageIndex + 1 > totalPages) {
      setPageIndex(totalPages - 1)
    }
  }, [totalPages, pageIndex])

  useEffect(() => {
    const sortParams = getSortParam(sorting)
    const searchParam = getSearchParam(searchString)
    const getDatasets = async () => {
      try {
        const datasetsResponse = await fetch(
          `${URL}/datasets?_page=${pageIndex + 1}&_limit=${pageSize}${sortParams}${searchParam}`,
        )
        const datasetsData: DatasetResponse[] = await datasetsResponse.json()
        const datasets = mapDatasets(datasetsData)
        setDatasets(datasets)
        const totalCount = Number(datasetsResponse.headers.get('X-Total-Count')) || 0
        if (rowCount !== totalCount) {
          setRowCount(totalCount)
        }
      } catch (error) {
        console.error(error)
      }
    }
    getDatasets()
  }, [pageIndex, pageSize, URL, rowCount, sorting, searchString])

  const CustomElement = (
    <Button variant="secondary">
      <Plus />
      {t('newDataset')}
    </Button>
  )

  return (
    <div className="h-full min-h-full max-h-full">
      <PageHeader title={t('title')} subtitle={t('subtitle')} customElement={CustomElement} />
      <SearchField setSearchString={setSearchString} aria-label={t('searchDatasets')} />
      <TableContainer>
        <DatasetsTable
          datasets={datasets}
          rowCount={rowCount}
          pageIndex={pageIndex}
          setPageIndex={setPageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          sorting={sorting}
          setSorting={setSorting}
          totalPages={totalPages}
        />
      </TableContainer>
    </div>
  )
}

export default DatasetsPage
