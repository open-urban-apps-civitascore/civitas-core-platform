import { getTranslations } from 'next-intl/server'

import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

interface ApisPageProps {
  params: Promise<{ datasetId: string }>
  searchParams: Promise<{ type?: string }>
}

const ApisPage = async (props: ApisPageProps) => {
  const t = await getTranslations('datasets')
  const { type } = await props.searchParams

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('overview.completion.apis.title')} />
      <div className="p-6">API setup placeholder — type: {type ?? 'none'}</div>
    </PageContainer>
  )
}

export default ApisPage
