'use client'

import { useTranslations } from 'next-intl'
import { ReactNode, useEffect } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'

import { Button } from '../ui/button'

export type TabValue = Record<string, Tab>

interface DetailsPageLayoutProps {
  title: string
  tabValues: TabValue
  children: ReactNode
  onCancelClick: () => void
  selectedTab: string
  onSelectTab: (newTab: string) => void
}

export const DetailsPageLayout = (props: DetailsPageLayoutProps) => {
  const { tabValues, title, selectedTab, onSelectTab, onCancelClick, children } = props
  const t = useTranslations('common')

  const tabs: Tab[] = Object.values(tabValues)
  console.log(tabs)

  useEffect(() => {
    if (!selectedTab) {
      onSelectTab(tabs[0].value)
    }
  }, [selectedTab, tabs, onSelectTab])

  return (
    <PageContainer headerType="withSubTabs">
      <PageHeader title={title} subTabs={{ tabs: tabs, selectedTab, onClick: onSelectTab }} />
      <PageBackground className="flex flex-col justify-between">
        <ContentCard>{children}</ContentCard>
        <div className="w-full flex gap-4 justify-end">
          <Button type="reset" variant="secondary" onClick={onCancelClick}>
            {t('actions.cancel')}
          </Button>
          <Button type="submit">{t('actions.submit')}</Button>
        </div>
      </PageBackground>
    </PageContainer>
  )
}
