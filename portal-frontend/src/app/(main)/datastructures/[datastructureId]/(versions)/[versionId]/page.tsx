import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import { DatastructureApiResponseSchema, DatastructureVersionApiResponseSchema } from '@/types/datastructures'

import { VersionOverview } from '../components/VersionOverview'

interface EditDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const EditDatastructureVersionPage = async ({ params }: EditDatastructureVersionPage) => {
  const { versionId, datastructureId } = await params
  const tCommon = await getTranslations('common')
  const datastructureResponse = await getDatastructure(datastructureId)
  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error('Error while parsing datastrcuture API response.')
    throw new Error()
  }
  const versionResponse = await getDatastructureVersion(datastructureId, versionId)
  const parsedVersion = DatastructureVersionApiResponseSchema.safeParse(versionResponse.data)
  if (!parsedVersion.success) {
    console.error('Error while parsing datastrcuture version API response.')
    throw new Error()
  }

  return (
    <VersionOverview
      // A version has no number until its model is stored, so the heading names the state instead.
      title={parsedVersion.data.version ? `Version ${parsedVersion.data.version}` : tCommon('status.DRAFT')}
      datastructure={parsedDatastructure.data}
      version={parsedVersion.data}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default EditDatastructureVersionPage
