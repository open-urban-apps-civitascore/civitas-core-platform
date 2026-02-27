import { getTranslations } from 'next-intl/server'

import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema, DatastructureVersionApiResponseSchema } from '@/types/datastructures'

import { VersionOverview } from '../components/VersionOverview'

interface EditDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const EditDatastructureVersionPage = async ({ params }: EditDatastructureVersionPage) => {
  const { versionId, datastructureId } = await params
  const t = await getTranslations('common')
  const datastructureResponse = await getDatastructure(datastructureId)
  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }
  console.log('datastructure', parsedDatastructure.data)
  const versionResponse = await getDatastructureVersion(datastructureId, versionId)
  const parsedVersion = DatastructureVersionApiResponseSchema.safeParse(versionResponse.data)
  if (!parsedVersion.success) {
    console.error(t('errors.loadingError'), parsedVersion)
    return <NoDataPage title={t('errors.loadingError')} />
  }
  return (
    <VersionOverview
      title={`Version ${parsedVersion.data.version}`}
      datastructureId={datastructureId}
      existingVersions={parsedDatastructure.data.dataStructureVersions?.map(version => version.version)}
      version={parsedVersion.data}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default EditDatastructureVersionPage
