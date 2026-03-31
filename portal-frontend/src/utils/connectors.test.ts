import { CONNECTOR_TYPES } from '@/const/connectors'

import { getConnectorDefaults, getConnectorFormData } from './connectors'

vi.mock('@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources', () => ({
  CONNECTOR_INPUTS: {
    MQTT: [
      { key: 'urls', defaultValue: '' },
      { key: 'topics', defaultValue: '' },
      { key: 'connect_timeout', defaultValue: '3s' },
    ],
    SQL: [
      { key: 'columns', defaultValue: '' },
      { key: 'init_files', defaultValue: '' },
    ],
  },
}))

describe('getConnectorDefaults', () => {
  it('returns mqtt defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.MQTT)
    expect(result).toEqual({
      urls: '',
      topics: '',
      connect_timeout: '3s',
    })
  })

  it('returns sql defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.SQL)
    expect(result).toEqual({
      columns: '',
      init_files: '',
    })
  })
})

describe('getConnectorFormData', () => {
  it('merges defaults with existing config when provided', () => {
    const existingConfig = { urls: 'a', topics: 'b' }

    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, existingConfig)

    expect(result).toEqual({
      urls: 'a',
      topics: 'b',
      connect_timeout: '3s',
    })
  })

  it('creates defaults when no existing config', () => {
    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, undefined)

    expect(result).toEqual({
      urls: '',
      topics: '',
      connect_timeout: '3s',
    })
  })
})
