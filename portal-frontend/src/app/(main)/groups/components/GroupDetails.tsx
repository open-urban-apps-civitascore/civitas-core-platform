'use client'

import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Group } from '@/types/groups'

import { BaseInfoTab } from './BaseInfoTab'

const LoadingSkeleton = () => (
  <div>
    <Skeleton />
    <Skeleton />
  </div>
)

interface GroupDetailsProps {
  title: string
  groupData: Group | null
  isEditMode?: boolean
}
const GroupDetails = (props: GroupDetailsProps) => {
  const { title, groupData, isEditMode = false } = props
  const { subTabValue, setSubTabValueParam } = useQueryParams()

  const t = useTranslations('groups')
  const tabValues = {
    info: {
      value: 'info',
      label: t('detailsTabs.info'),
    },
    roles: {
      value: 'roles',
      label: t('detailsTabs.roles'),
    },
    users: {
      value: 'users',
      label: t('detailsTabs.users'),
    },
    subgroups: {
      value: 'subgroups',
      label: t('detailsTabs.subgroups'),
    },
  }

  const tabs: Tab[] = Object.values(tabValues)

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(tabs[0].value)
    }
  }, [subTabValue, tabs, setSubTabValueParam])

  if (!subTabValue) {
    return (
      <PageContainer headerType="withSubTabs">
        <PageHeader title={title} subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: setSubTabValueParam }} />
        <PageBackground>
          <LoadingSkeleton />
        </PageBackground>
      </PageContainer>
    )
  }

  return (
    <PageContainer headerType="withSubTabs">
      <PageHeader title={title} subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: setSubTabValueParam }} />
      <PageBackground>
        {groupData && subTabValue === tabValues.info.value && (
          <BaseInfoTab isEditMode={isEditMode} groupData={groupData} />
        )}
        {groupData && subTabValue !== tabValues.info.value && (
          <ContentCard>{tabValues[subTabValue as keyof typeof tabValues].label}</ContentCard>
        )}
        {!groupData && <ContentCard>No data</ContentCard>}
      </PageBackground>
    </PageContainer>
  )
}

export default GroupDetails
