import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureApiResponseSchema,
  DatastructureVersionApiResponseSchema,
} from '@/types/datastructures'

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

  const otherExistingVersions = parsedDatastructure.data.dataStructureVersions?.flatMap(version =>
    version.id !== versionId
      ? {
          id: version.id,
          version: version.version,
          dataStructureVersionStatus: version.dataStructureVersionStatus,
        }
      : [],
  )

  return (
    <VersionOverview
      title={`Version ${parsedVersion.data.version}`}
      datastructureId={datastructureId}
      isDatastructureAvailable={parsedDatastructure.data.dataStructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE}
      otherVersions={otherExistingVersions}
      version={parsedVersion.data}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default EditDatastructureVersionPage
