'use client'

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
  dataRoom: string
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
    dataRoom: dataset.series.title,
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

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const [datasets, setDatasets] = useState<Dataset[]>([])

  useEffect(() => {
    const getData = async () => {
      const data = await fetch(
        `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}/datasets`,
      )
      const datasetsResponse: DatasetResponse[] = await data.json()
      const datasets = mapDatasets(datasetsResponse)
      setDatasets(datasets)
    }
    getData()
  }, [])

  return (
    <div className="w-full h-full">
      <div className="flex justify-between my-6">
        <div>
          <h1 className="text-xl font-semibold my-1">{t('title')}</h1>
          <p className="text-primary-light">{t('subtitle')}</p>
        </div>
        <Button variant="secondary">
          <Plus />
          {t('newDataset')}
        </Button>
      </div>
      <DatasetsTable datasets={datasets} />
    </div>
  )
}

export default DatasetsPage
