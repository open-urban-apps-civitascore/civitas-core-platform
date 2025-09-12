import { Plus } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { Button } from '@/components/ui/button'

import DatasetsTable from './components/DatasetsTable'

const DatasetsPage = async () => {
  const t = await getTranslations('datasets')

  return (
    <div className="w-full h-full">
      <div className="flex justify-between my-6">
        <div>
          <h1 className="text-xl font-semibold my-1">{t('title')}</h1>
          <p className="text-primary-light">{t('subtitle')}</p>
        </div>
        <Button variant="secondary">
          <Plus />
          {t('newDataset')}
        </Button>
      </div>
      <DatasetsTable />
    </div>
  )
}

export default DatasetsPage
