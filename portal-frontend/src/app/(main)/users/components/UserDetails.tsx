'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/use-query-params'
import { User } from '@/types/users'

import { GroupsTab } from './groups-tab/GroupsTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UserForm } from './UserForm'

interface UserDetailsProps {
  title: string
  userData: User | null
  isEditMode?: boolean
  testId?: string
}

export const UserDetails = (props: UserDetailsProps) => {
  const { title, userData, isEditMode = false, testId } = props
  const t = useTranslations('users')
  const { setSubTabValueParam, subTabValue } = useQueryParams()

  const tabValues: Record<'userData' | 'roles' | 'groups' | 'dataSpaces' | 'account', Tab> = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userDetails',
      isActive: true,
    },
    groups: {
      label: t('detailsTabs.groups'),
      value: 'userGroups',
      isActive: isEditMode,
    },
    dataSpaces: {
      label: t('detailsTabs.dataspaces'),
      value: 'dataspaces',
      isActive: isEditMode,
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
      isActive: isEditMode,
    },
    account: {
      label: t('detailsTabs.account'),
      value: 'account',
      isActive: isEditMode,
    },
  }
  const tabs: Tab[] = [tabValues.userData, tabValues.groups, tabValues.dataSpaces, tabValues.roles, tabValues.account]

  const defaultTab = tabValues.userData.value

  const isBlockedTab = useMemo(() => !isEditMode && subTabValue !== defaultTab, [isEditMode, subTabValue, defaultTab])

  useEffect(() => {
    if (!subTabValue || isBlockedTab) {
      setSubTabValueParam(defaultTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultTab, isBlockedTab])

  const handleSelectTab = (newTab: string) => {
    setSubTabValueParam(newTab)
  }

  let Content = <ContentCard>No data</ContentCard>
  if (userData) {
    switch (subTabValue) {
      case tabValues.userData.value:
      case '':
        Content = <UserForm userData={userData} isEditMode={isEditMode} />
        break
      case tabValues.groups.value:
        Content = <GroupsTab userId={userData.id} />
        break
      case tabValues.roles.value:
        Content = <RolesTab groupIds={userData.groups} />
        break
      default:
        Content = <ContentCard>No data</ContentCard>
        break
    }
  }

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        subTabs={{ tabs: tabs, selectedTab: subTabValue || defaultTab, onClick: newTab => handleSelectTab(newTab) }}
      />
      <PageBackground>{userData ? Content : <NoDataPage title={t('notFound')} />}</PageBackground>
    </PageContainer>
  )
}
