'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useRouter } from 'next/navigation'
import { useForm } from 'react-hook-form'

import { DetailsPageLayout } from '@/components/layout/DetailsPageLayout'
import { useQueryParams } from '@/hooks/useQueryParams'
import { CreateGroupData, GroupData, GroupSchema } from '@/types/groups'

import { BaseInfoTab } from '../components/BaseInfoTab'
import { useEffect } from 'react'

const defaultGroup: GroupData = {
  id: '',
  title: '',
  description: '',
  roles: [],
  users: [],
  contact: '',
  subgroups: [],
}

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

const createGroup = async (groupData: CreateGroupData) => {
  try {
    const response = await fetch(`${URL}/groups`, {
      method: 'POST',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(groupData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }
    const data = await response.json()
    console.log('successfully created group:', data)
  } catch (error) {
    console.error('An error occurred while creating new group:', error)
  }
}

const CreateGroupPage = () => {
  const { subTabValue, setSubTabValueParam, setApiRequestParams } = useQueryParams()
  const router = useRouter()

  const t = useTranslations('groups')
  const tabs = {
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

  const goToGroupsList = () => {
    const apiParams = setApiRequestParams()
    router.push(`/users?${apiParams}`)
  }

  const form = useForm<GroupData>({
    resolver: zodResolver(GroupSchema),
    defaultValues: { ...defaultGroup },
  })

  const handleCreateGroup = (groupData: GroupData) => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = groupData
    createGroup(createGroupData)
    router.push('/users')
  }
  

  return (
    <DetailsPageLayout
      tabValues={tabs}
      selectedTab={subTabValue}
      onSelectTab={setSubTabValueParam}
      title={t('createGroup')}
      onCancelClick={goToGroupsList}
    >
      {subTabValue === tabs.info.value && form && <BaseInfoTab form={form} onSubmit={handleCreateGroup} />}
    </DetailsPageLayout>
  )
}

export default CreateGroupPage
