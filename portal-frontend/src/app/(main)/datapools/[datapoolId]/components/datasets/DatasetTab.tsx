'use client'

import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { useGetDatasets } from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { DATASET_FILTER_PARAMS } from '@/const/searchParams'
import { useQueryParams } from '@/hooks/use-query-params'
import { BaseDatasetTableData, Dataset } from '@/types/datasets'

import { DatasetTable } from './DatasetTable'

type DatasetTabProps = {
  datapoolId: string
  isReadOnly?: boolean
  isCreateMode?: boolean
}

const toDatasetTableData = (dataset: Dataset): BaseDatasetTableData => ({
  id: dataset.id,
  name: dataset.name,
  createdBy: dataset.createdBy,
  modifiedAt: dataset.modifiedAt,
  dataSetStatus: dataset.dataSetStatus,
})

export const DatasetTab = (props: DatasetTabProps) => {
  const { datapoolId, isReadOnly, isCreateMode = false } = props
  const t = useTranslations('datapools.overview.datasetsTab')

  const {
    pageIndex,
    pageSize,
    sorting,
    search,
    totalPages,
    setTotalPages,
    setPaginationParams,
    setSortingParams,
    setSearchParam,
    getApiRequestParamsByUrl,
  } = useQueryParams()

  const params = getApiRequestParamsByUrl()
  params.set(DATASET_FILTER_PARAMS.datapoolIds, datapoolId)

  const { data: datasets, isLoading } = useGetDatasets({ params, isEnabled: !isCreateMode })

  const rows = datasets?.data?.map(toDatasetTableData) ?? []
  const rowCount = datasets?.totalElements ?? 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize) || 1)
  }, [rowCount, pageSize, setTotalPages])

  const addDatasetButton =
    !isLoading && !isReadOnly ? (
      <GuardedLink href={`/datasets/create?datapoolId=${datapoolId}&source=datapools`}>
        <Button type="button">{t('addDataset')}</Button>
      </GuardedLink>
    ) : null

  return (
    <ContentCard className="h-full overflow-auto">
      <SubHeader title={t('title')} titleClassName="text-2xl font-bold" className="mb-4" />
      {isCreateMode || (datasets?.data.length === 0 && !isLoading && !search) ? (
        <NoDataPage
          title={t('noDatasets')}
          subTitle={t('noDatasetsSubTitle')}
          customElement={!isCreateMode ? addDatasetButton : undefined}
        />
      ) : (
        <>
          <SearchHeader
            searchString={search}
            onChangeSearchString={setSearchParam}
            aria-label={t('searchDatapools')}
            customElement={addDatasetButton}
          />
          <TableContainer>
            <DatasetTable
              datasets={rows}
              rowCount={rowCount}
              pageIndex={pageIndex}
              pageSize={pageSize}
              totalPages={totalPages}
              sorting={sorting}
              isLoading={isLoading}
              onPaginationChange={setPaginationParams}
              onSortingChange={setSortingParams}
            />
          </TableContainer>
        </>
      )}
    </ContentCard>
  )
}
