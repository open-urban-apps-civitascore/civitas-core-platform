'use client'

import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { DatasetResponse, DatasetTableData } from '@/types/datasets'

import DatasetsTable from './components/DatasetsTable'

const DatasetsPage = () => {
  const t = useTranslations('datasets')
  const router = useRouter()
  const [datasets, setDatasets] = useState<DatasetTableData[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [isLoading, setIsLoading] = useState(true)

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

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  useEffect(() => {
    const params = getApiRequestParamsByUrl()

    const getDatasets = async () => {
      setIsLoading(true)
      try {
        const datasetsResponse = await fetch(`/api/datasets?${params.toString()}`)
        if (!datasetsResponse.ok) {
          throw new Error('An error occurred while loading data')
        }
        const datasetsData: DatasetResponse[] = await datasetsResponse.json()
        setDatasets(datasetsData)
        const totalCount = Number(datasetsResponse.headers.get('X-Total-Count')) || 0
        if (rowCount !== totalCount) {
          setRowCount(totalCount)
        }
        setTotalPages(Math.ceil(totalCount / pageSize))
      } catch (error) {
        console.error(error)
      } finally {
        setIsLoading(false)
      }
    }
    getDatasets()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, getApiRequestParamsByUrl])

  const CustomElement = (
    <Button onClick={() => router.push(`datasets/create?${getApiRequestParamsByUrl().toString()}`)}>
      <Plus />
      {t('newDataset')}
    </Button>
  )

  return (
    <PageContainer testId="datasetsPage" headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader
          searchString={search}
          onChangeSearchString={setSearchParam}
          aria-label={t('searchDatasets')}
          customElement={CustomElement}
        />
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
            isLoading={isLoading}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default DatasetsPage
