'use client'

import Image from 'next/image'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group, GroupTab } from '@/types/groups'

import Icon from '../../../../../public/svg/info.svg'
import { BaseInfoTab } from './BaseInfoTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UsersTab } from './users-tab/UsersTab'
interface GroupDetailsProps {
  title: string
  groupData: Group
  isEditMode?: boolean
}
export const GroupDetails = (props: GroupDetailsProps) => {
  const { title, groupData, isEditMode = false } = props
  const { subTabValue, setSubTabValueParam } = useQueryParams()

  const t = useTranslations('groups')
  const tabValues: Record<GroupTab, Tab<GroupTab>> = {
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

  const disabledTabs = !isEditMode ? ['roles', 'users', 'subgroups'] : undefined

  const tabs = Object.values(tabValues)

  const defaultTab = tabValues.info.value

  const isBlockedTab = useMemo(() => !isEditMode && subTabValue !== defaultTab, [isEditMode, subTabValue, defaultTab])

  useEffect(() => {
    if (!subTabValue || isBlockedTab) {
      setSubTabValueParam(tabs[0].value)
    }
  }, [subTabValue, tabs, setSubTabValueParam, isBlockedTab])

  let Content = <BaseInfoTab isEditMode={isEditMode} groupData={groupData} />
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

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={title}
        segmentedControlBarSectionProps={{
          tabs: tabs,
          selectedTab: subTabValue,
          onTabChange: setSubTabValueParam,
          disabledTabs,
        }}
      />
      <PageBackground className="flex flex-col">
        {!isEditMode && (
          <ContentCard className="flex flex-row gap-2 p-4 mb-5 text-sm">
            <Image src={Icon} alt="Info icon" width={20} height={20} />
            {t('createHint')}
          </ContentCard>
        )}
        {Content}
      </PageBackground>
    </PageContainer>
  )
}
