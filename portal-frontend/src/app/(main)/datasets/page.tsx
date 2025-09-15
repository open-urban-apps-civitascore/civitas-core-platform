'use client'

import { SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/button'

import DatasetsTable from './components/DatasetsTable'

export type Status = 'open' | 'closed' | null
type Creator = { id: string; firstName: string; lastName: string }
type Distribution = {
  format: string
  title: string
  url: string
}
type Category = { id: string; title: string }

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
    const sortParam = sorting[0]?.desc ? `&_sort=-${sortingId}` : `&_sort=${sortingId}`
    return sortParam
  }
  return ''
}

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [pageIndex, setPageIndex] = useState(0)
  const [sorting, setSorting] = useState<SortingState>([])

  useEffect(() => {
    console.log(sorting)
  }, [sorting])

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  useEffect(() => {
    const sortParams = getSortParam(sorting)
    const getDatasets = async () => {
      try {
        const datasetsResponse = await fetch(
          `${URL}/datasets?_page=${pageIndex + 1}&_per_page=${pageSize}${sortParams}`,
        )
        const datasetsData = await datasetsResponse.json()
        const datasets = mapDatasets(datasetsData.data as DatasetResponse[])
        setDatasets(datasets)
        if (rowCount === 0) {
          setRowCount(datasetsData.items)
        }
      } catch (error) {
        console.error(error)
      }
    }
    getDatasets()
  }, [pageIndex, pageSize, URL, rowCount, sorting])

  return (
    <div className="w-full h-full flex-1 [--title-height:calc(--spacing(28))] [--page-padding:calc(--spacing(4))]">
      <div className="flex items-center justify-between h-[var(--title-height)]">
        <div className="">
          <h1 className="text-xl font-semibold my-1">{t('title')}</h1>
          <p className="text-primary-light">{t('subtitle')}</p>
        </div>
        <Button variant="secondary">
          <Plus />
          {t('newDataset')}
        </Button>
      </div>
      <div className="h-[calc(100%-var(--title-height))]">
        <DatasetsTable
          datasets={datasets}
          rowCount={rowCount}
          pageIndex={pageIndex}
          setPageIndex={setPageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          sorting={sorting}
          setSorting={setSorting}
        />
      </div>
    </div>
  )
}

export default DatasetsPage
