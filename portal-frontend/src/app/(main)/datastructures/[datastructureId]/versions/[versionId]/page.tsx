import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema } from '@/types/datastructures'

import { VersionOverview } from '../VersionOverview'

interface PageProps {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const DatasourceDetailsPage = async ({ params }: PageProps) => {
  const { versionId, datastructureId } = await params
  const t = await getTranslations('common')
  const datastructureResponse = await getDatastructure(datastructureId)
  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  return (
    <VersionOverview
      title=""
      datastructure={parsedDatastructure.data}
      versionId={versionId}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default DatasourceDetailsPage
