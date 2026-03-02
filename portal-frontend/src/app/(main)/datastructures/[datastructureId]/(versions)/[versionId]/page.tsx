
import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import { DatastructureApiResponseSchema, DatastructureVersionApiResponseSchema } from '@/types/datastructures'

import { VersionOverview } from '../components/VersionOverview'

interface EditDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const EditDatastructureVersionPage = async ({ params }: EditDatastructureVersionPage) => {
  const { versionId, datastructureId } = await params
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
      title={`Version ${parsedVersion.data.version}`}
      datastructureId={datastructureId}
      existingVersions={parsedDatastructure.data.dataStructureVersions?.map(version => ({
        id: version.id,
        version: version.version,
      }))}
      version={parsedVersion.data}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default EditDatastructureVersionPage
