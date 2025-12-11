'use client'

import { useTranslations } from 'next-intl'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

const TableEditorPage = () => {
  const t = useTranslations('datasets')

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('dataMode.tableEditor')} />
      <PageBackground>
        <div className="flex h-full items-center justify-center">
          <p className="text-muted-foreground">{t('dataMode.tableEditor')}</p>
        </div>
      </PageBackground>
    </PageContainer>
  )
}

export default TableEditorPage
