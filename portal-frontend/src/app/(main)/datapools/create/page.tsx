import { getTranslations } from 'next-intl/server'

import { Datapool } from '@/types/datapools'

import { DatapoolOverview } from '../[datapoolId]/components/DatapoolOverview'

const defaultDatapool: Datapool = {
  id: '',
  name: '',
  description: '',
  contactPerson: null,
  createdAt: '',
  modifiedAt: '',
}

const CreateDatapoolPage = async () => {
  const t = await getTranslations('datapools')

  return <DatapoolOverview datapool={defaultDatapool} isCreateMode={true} title={t('create.title')} />
}

export default CreateDatapoolPage
