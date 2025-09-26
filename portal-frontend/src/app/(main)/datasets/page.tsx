'use client'

import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'

import DatasetsTable from './components/DatasetsTable'

export type Status = 'open' | 'closed' | null
export type Creator = { id: string; firstName: string; lastName: string }
export type Distribution = {
  format: string
  name: string
  url: string
}

export type Category = { id: string; name: string }

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
  name: string
  creator: Creator[]
  issued: string
  lastUpdated: string
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
    name: dataset.name,
    dataSpace: dataset.series.name,
    department: dataset.department.name,
    creator: dataset.creator.map(creator => `${creator.firstName} ${creator.lastName}`),
    lastUpdated: dataset.lastUpdated,
    status: dataset.status,
    releaseProcess: null,
    distribution: dataset.distribution
      ? {
          format: dataset.distribution?.format,
          name: dataset.distribution?.name,
          url: dataset.distribution?.url,
        }
      : null,
  }))

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [rowCount, setRowCount] = useState(0)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setApiRequestParams,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  useEffect(() => {
    const params = setApiRequestParams(totalPages)

    const getDatasets = async () => {
      try {
        const datasetsResponse = await fetch(`${URL}/datasets?${params.toString()}`)
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
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, setApiRequestParams, totalPages])

  const CustomElement = (
    <Button variant="secondary">
      <Plus />
      {t('newDataset')}
    </Button>
  )

  return (
    <div className="h-full min-h-full max-h-full">
      <PageHeader title={t('title')} subtitle={t('subtitle')} customElement={CustomElement} />
      <SearchField searchString={search} onChangeSearchString={setSearchParam} aria-label={t('searchDatasets')} />
      <TableContainer>
        <DatasetsTable
          datasets={datasets}
          rowCount={rowCount}
          pageIndex={pageIndex}
          pageSize={pageSize}
          sorting={sorting}
          totalPages={totalPages}
          onPaginationChange={setPaginationParams}
          onSortingChange={setSortingParams}
        />
      </TableContainer>
    </div>
  )
}

export default DatasetsPage
