import { getTranslations } from 'next-intl/server'

import DatasetsTable from './components/DatasetsTable'

const Datasets = async () => {
  const t = await getTranslations('datasets')

  return (
    <div className="w-full h-full ">
      <div className="my-6">
        <h1 className="text-xl font-semibold my-1">{t('title')}</h1>
        <p className="text-primary-light">{t('subtitle')}</p>
      </div>
      <DatasetsTable />
    </div>
  )
}

export default Datasets
