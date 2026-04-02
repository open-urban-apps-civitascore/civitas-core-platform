import { getTranslations } from 'next-intl/server'

import { getDatasources } from '@/app/services/api/datasources/serverRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
import { ApiRequestParams, getApiRequestParams } from '@/utils/requestParams'

import { DatasourcesList } from './components/list/DatasourcesList'

type Props = {
  searchParams: Promise<ApiRequestParams>
}

const DatasourcesPage = async ({ searchParams }: Props) => {
  const t = await getTranslations('datasources')
  const params = await searchParams

  const { apiParams, pageSize, sort, pageIndex, search } = getApiRequestParams(params)
  const { data, totalElements } = await getDatasources(apiParams)

  const totalCount = Number(totalElements) || 0
  const totalPages = Math.ceil(totalCount / pageSize) || 1
  const sorting = sort.map((entry: string) => ({ id: entry.split(',')[0], desc: entry.split(',')[1] === 'DESC' }))

  return (
    <PageContainer headerType="withSubTabsOrSubtitle" testId="datasourcesPage">
      <PageHeader title={t('title')} subtitle={t('subtitle')} />
      <PageBackground>
        <TableContainer>
          <DatasourcesList
            datasources={data ?? []}
            totalCount={totalCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            search={search}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default DatasourcesPage
