'use client'

import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { DATASOURCE_FILTER_PARAMS } from '@/const/searchParams'
import { useQueryParams } from '@/hooks/use-query-params'

import { DatasourceTable } from './DatasourceTable'

type DatasourceTabProps = {
  datapoolId: string
  isCreateMode?: boolean
}

export const DatasourceTab = (props: DatasourceTabProps) => {
  const { datapoolId, isCreateMode = false } = props
  const t = useTranslations('datapools.overview.datasourcesTab')

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
  params.set(DATASOURCE_FILTER_PARAMS.datapoolId, datapoolId)

  const { data: datasources, isLoading } = useGetDatasources({ params, isEnabled: !isCreateMode })

  const rows = datasources?.data ?? []
  const rowCount = datasources?.totalElements ?? 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize) || 1)
  }, [rowCount, pageSize, setTotalPages])

  return (
    <ContentCard className="h-full overflow-auto">
      <SubHeader title={t('title')} subtitle={t('subtitle')} titleClassName="text-2xl font-bold" className="mb-4" />
      {isCreateMode || (rows.length === 0 && !isLoading && !search) ? (
        <NoDataPage title={t('noDatasources')} subTitle={t('noDatasourcesSubTitle')} />
      ) : (
        <>
          <SearchHeader searchString={search} onChangeSearchString={setSearchParam} aria-label={t('search')} />
          <TableContainer>
            <DatasourceTable
              datasources={rows}
              rowCount={rowCount}
              pageIndex={pageIndex}
              pageSize={pageSize}
              totalPages={totalPages}
              sorting={sorting}
              isLoading={isLoading}
              onPaginationChange={setPaginationParams}
              onSortingChange={setSortingParams}
              isPaginationHidden={rowCount <= pageSize}
            />
          </TableContainer>
        </>
      )}
    </ContentCard>
  )
}
