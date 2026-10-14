import { useTranslations } from 'next-intl'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'

export const ApiCallsTab = () => {
  const t = useTranslations('datasources.apiCallsTab')

  return (
    <ContentCard className="h-full overflow-auto" footerElement={<FooterElement />}>
      <SubHeader title={t('title')} className="pb-4 border-b-1" />
    </ContentCard>
  )
}
