import { getTranslations } from 'next-intl/server'

import { Group } from '@/types/groups'

import { GroupDetails } from '../components/GroupDetails'

const defaultGroup: Group = {
  id: '',
  name: '',
  description: '',
  roles: null,
  members: null,
  contactUser: null,
  createdAt: '',
  modifiedAt: '',
}

const CreateGroupPage = async () => {
  const t = await getTranslations('groups')

  return <GroupDetails title={t('createGroup')} groupData={defaultGroup} />
}

export default CreateGroupPage
