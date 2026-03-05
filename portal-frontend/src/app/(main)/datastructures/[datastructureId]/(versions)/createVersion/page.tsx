import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { DATASTRUCTURE_STATUS_TYPES, DatastructureApiResponseSchema } from '@/types/datastructures'

import { VersionOverview } from '../components/VersionOverview'

interface CreateDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const CreateDatastructureVersionPage = async ({ params }: CreateDatastructureVersionPage) => {
  const { datastructureId } = await params
  const t = await getTranslations('datastructureVersions')

  const datastructureResponse = await getDatastructure(datastructureId)

  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error('Error while parsing datastrcuture API response.')
    throw new Error()
  }

  return (
    <VersionOverview
      otherVersions={parsedDatastructure.data.dataStructureVersions?.map(version => ({
        id: version.id,
        version: version.version,
        dataStructureVersionStatus: version.dataStructureVersionStatus,
      }))}
      isDatastructureAvailable={parsedDatastructure.data.dataStructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE}
      testId="createDatastructureVersionOverview"
      version={null}
      datastructureId={datastructureId}
      title={t('newVersion')}
      isCreateMode
    />
  )
}

export default CreateDatastructureVersionPage
