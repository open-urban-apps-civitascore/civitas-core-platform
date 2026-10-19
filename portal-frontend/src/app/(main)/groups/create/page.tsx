import { getTranslations } from 'next-intl/server'

import { Group } from '@/types/groups'

import { GroupOverview } from '../components/GroupOverview'

const defaultGroup: Group = {
  id: '',
  name: '',
  description: '',
  assignments: null,
  members: null,
  contactUser: null,
  createdAt: '',
  modifiedAt: '',
}

const CreateGroupPage = async () => {
  const t = await getTranslations('groups')

  return <GroupOverview title={t('createGroup')} groupData={defaultGroup} isCreateMode={true} />
}

export default CreateGroupPage
