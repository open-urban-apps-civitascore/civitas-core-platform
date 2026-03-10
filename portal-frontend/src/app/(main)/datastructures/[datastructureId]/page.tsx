import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema } from '@/types/datastructures'

import { DatastructureOverview } from './components/DatastructureOverview'

type EditDatastructurePageProps = {
  params: Promise<{ datastructureId: string }>
}

const EditDatastructurePage = async ({ params }: EditDatastructurePageProps) => {
  const { datastructureId } = await params
  const t = await getTranslations('common')
  const datastructureResponse = await getDatastructure(datastructureId)
  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  return <DatastructureOverview datastructure={parsedDatastructure.data} />
}

export default EditDatastructurePage
