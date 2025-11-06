'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Group } from '@/types/groups'

import { BaseInfoTab } from './BaseInfoTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UsersTab } from './users-tab/UsersTab'

interface GroupDetailsProps {
  title: string
  groupData: Group | null
  isEditMode?: boolean
}
const GroupDetails = (props: GroupDetailsProps) => {
  const { title, groupData, isEditMode = false } = props
  const { subTabValue, setSubTabValueParam } = useQueryParams()

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

  const defaultTab = tabValues.info.value

  const isBlockedTab = useMemo(() => !isEditMode && subTabValue !== defaultTab, [isEditMode, subTabValue, defaultTab])

  useEffect(() => {
    if (!subTabValue || isBlockedTab) {
      setSubTabValueParam(tabs[0].value)
    }
  }, [subTabValue, tabs, setSubTabValueParam, isBlockedTab])

  let Content = <LoadingSpinner className="h-full" />
  if (!groupData) {
    Content = <ContentCard>No data</ContentCard>
  } else if (!isBlockedTab) {
    switch (subTabValue) {
      case tabValues.info.value:
        Content = <BaseInfoTab isEditMode={isEditMode} groupData={groupData} />
        break
      case tabValues.roles.value:
        Content = <RolesTab groupData={groupData} />
        break
      case tabValues.users.value:
        Content = <UsersTab groupData={groupData} />
        break
      case tabValues.subgroups.value:
        Content = <ContentCard>{tabValues[subTabValue as keyof typeof tabValues].label}</ContentCard>
        break
      default:
        break
    }
  }

  return (
    <PageContainer headerType="withSubTabs">
      <PageHeader title={title} subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: setSubTabValueParam }} />
      <PageBackground>{Content}</PageBackground>
    </PageContainer>
  )
}

export default GroupDetails
