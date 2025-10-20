'use client'

import { useRouter } from 'next/navigation'
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
import { RolesTab } from './RolesTab'

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
  const { subTabValue, setSubTabValueParam, setApiRequestParams } = useQueryParams()
  const router = useRouter()

  const t = useTranslations('groups')
  const tabValues: Record<'info' | 'roles' | 'users' | 'subgroups', Tab> = {
    info: {
      value: 'info',
      label: t('detailsTabs.info'),
      isActive: true,
    },
    roles: {
      value: 'roles',
      label: t('detailsTabs.roles'),
      isActive: isEditMode,
    },
    users: {
      value: 'users',
      label: t('detailsTabs.users'),
      isActive: isEditMode,
    },
    subgroups: {
      value: 'subgroups',
      label: t('detailsTabs.subgroups'),
      isActive: isEditMode,
    },
  }

  const tabs = Object.values(tabValues)

  useEffect(() => {
    if (!subTabValue) {
      setSubTabValueParam(tabs[0].value)
    }
  }, [subTabValue, tabs, setSubTabValueParam])

  let Content = <LoadingSkeleton />

  if (!groupData) {
    Content = <ContentCard>No data</ContentCard>
  } else {
    switch (subTabValue) {
      case tabValues.info.value:
        Content = <BaseInfoTab isEditMode={isEditMode} groupData={groupData}/>
        break
      case tabValues.roles.value:
        Content = <RolesTab groupData={groupData}/>
        break
      case tabValues.users.value:
      case tabValues.subgroups.value:
        Content = <ContentCard>{tabValues[subTabValue as keyof typeof tabValues].label}</ContentCard>
        break
      default:
        break
    }

    return (
      <PageContainer headerType="withSubTabs">
        <PageHeader title={title} subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: setSubTabValueParam }} />
        <PageBackground>{Content}</PageBackground>
      </PageContainer>
    )
  }
}

export default GroupDetails
