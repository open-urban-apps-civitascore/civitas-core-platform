import { getTranslations } from 'next-intl/server'

import { getDatasource } from '@/app/services/api/datasources/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatasourceSchema } from '@/types/datasources'

import { DatasourceOverview } from './components/DatasourceOverview'

type Props = {
  params: Promise<{ datasourceId: string }>
}

const DatasourceDetailsPage = async ({ params }: Props) => {
  const { datasourceId } = await params
  const t = await getTranslations('common')
  const datasourceResponse = await getDatasource(datasourceId)
  const parsedDatasource = DatasourceSchema.safeParse(datasourceResponse.data)
  if (!parsedDatasource.success) {
    console.error(t('errors.loadingError'), parsedDatasource)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  return <DatasourceOverview datasource={parsedDatasource.data} />
}

export default DatasourceDetailsPage
