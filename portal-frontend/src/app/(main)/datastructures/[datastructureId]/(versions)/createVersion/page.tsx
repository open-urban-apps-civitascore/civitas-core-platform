import { getTranslations } from 'next-intl/server'

import { VersionOverview } from '../components/VersionOverview'

interface CreateDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const CreateDatastructureVersionPage = async ({ params }: CreateDatastructureVersionPage) => {
  const { datastructureId } = await params

  const t = await getTranslations('datastructureVersion')

  return (
    <VersionOverview
      testId="createDatastructureVersionOverview"
      version={null}
      datastructureId={datastructureId}
      title={t('newVersion')}
      isCreateMode
    />
  )
}

export default CreateDatastructureVersionPage
