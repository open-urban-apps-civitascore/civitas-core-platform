import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { DatastructureApiResponseSchema } from '@/types/datastructures'

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
      testId="createDatastructureVersionOverview"
      version={null}
      datastructure={parsedDatastructure.data}
      title={t('newVersion')}
      isCreateMode
    />
  )
}

export default CreateDatastructureVersionPage
