import { Dataset } from '@/app/(main)/datasets/page'

export const mappedDatasets: Dataset[] = [
  {
    id: '1',
    name: 'Bebauungspläne der Stadt Musterstadt (XPlanung)',
    dataRoom: 'Verkehr',
    department: 'Stadtentwicklung',
    creator: ['Maximilian Müller'],
    lastUpdated: '2023-09-10T08:00:00Z',
    status: null,
    releaseProcess: null,
    distribution: null,
  },
  {
    id: '2',
    name: 'Spielplätze',
    dataRoom: 'Umwelt',
    department: 'Verkehrsplanung',
    creator: ['Sophie Schneider'],
    lastUpdated: '2023-01-01T08:00:00Z',
    status: 'open',
    releaseProcess: null,
    distribution: {
      format: 'DOCX',
      title: 'Superset',
      url: 'https://superset.de',
    },
  },
]
