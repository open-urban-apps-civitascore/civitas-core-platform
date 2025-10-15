'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Form } from '@/components/ui/form'
import { Skeleton } from '@/components/ui/skeleton'
import { useQueryParams } from '@/hooks/useQueryParams'
import { GroupData, GroupSchema } from '@/types/groups'
import { mapApiGroupData } from '@/utils/groups'

import { createGroup, updateGroup } from '../actions'
import { BaseInfoTab } from './BaseInfoTab'

const LoadingSkeleton = () => (
  <div>
    <Skeleton />
    <Skeleton />
  </div>
)

interface GroupDetailsProps {
  title: string
  groupData: GroupData | null
  isUpdateMode?: boolean
}
const GroupDetails = (props: GroupDetailsProps) => {
  const { title, groupData, isUpdateMode = false } = props
  const { subTabValue, setSubTabValueParam, setApiRequestParams } = useQueryParams()
  const router = useRouter()

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

  const goToGroupsList = () => {
    const apiParams = setApiRequestParams()
    router.push(`/groups?${apiParams}`)
  }

  const form = useForm<GroupData>({
    resolver: zodResolver(GroupSchema),
    defaultValues: { ...groupData },
  })

  const handleCreateGroup = async (formData: GroupData) => {
    const parsed = GroupSchema.parse(formData)
    const groupData = mapApiGroupData(parsed)

    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = groupData
    await createGroup(createGroupData)
    goToGroupsList()
  }

  const handleUpdateGroup = async (formData: GroupData) => {
    console.log('handleUpdateGroup')
    const parsed = GroupSchema.parse(formData)
    const groupData = mapApiGroupData(parsed)
    await updateGroup(groupData)
    goToGroupsList()
  }

  const handleSubmit = isUpdateMode ? handleUpdateGroup : handleCreateGroup

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
        {groupData ? (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col justify-between h-full">
              <ContentCard>
                {subTabValue === tabValues.info.value ? (
                  <BaseInfoTab form={form} />
                ) : (
                  <div>{tabValues[subTabValue as keyof typeof tabValues].label}</div>
                )}
              </ContentCard>

              <ActionButtons onCancelClick={goToGroupsList} confirmButtonType="submit" />
            </form>
          </Form>
        ) : (
          <ContentCard>No data</ContentCard>
        )}
      </PageBackground>
    </PageContainer>
  )
}

export default GroupDetails
