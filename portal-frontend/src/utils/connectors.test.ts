import { CONNECTOR_TYPES } from '@/const/connectors'
import { ConnectorDraft } from '@/types/connectors'

import { getConnectorDefaults, getConnectorFormData } from './connectors'

vi.mock('@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources', () => ({
  CONNECTORS: {
    MQTT: {
      properties: [
        { key: 'urls', defaultValue: '' },
        { key: 'topics', defaultValue: '' },
      ],
    },
    SQL: {
      properties: [
        { key: 'columns', defaultValue: '' },
        { key: 'init_files', defaultValue: '' },
      ],
    },
  },
}))

describe('getConnectorDefaults', () => {
  it('returns mqtt defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.MQTT)
    expect(result).toEqual({
      urls: '',
      topics: '',
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
  it('returns existing connector when type matches', () => {
    const existing: ConnectorDraft = {
      type: CONNECTOR_TYPES.MQTT,
      config: { urls: 'a', topics: 'b' },
    }

    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, existing)

    expect(result).toBe(existing)
  })

  it('creates new connector when type does not match', () => {
    const existing: ConnectorDraft = {
      type: CONNECTOR_TYPES.MQTT,
      config: { urls: 'a', topics: 'b' },
    }

    const result = getConnectorFormData(CONNECTOR_TYPES.SQL, existing)

    expect(result.type).toBe(CONNECTOR_TYPES.SQL)
    expect(result.config).toEqual({
      columns: '',
      init_files: '',
    })
  })

  it('creates new connector when no existing data', () => {
    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, null)

    expect(result).toEqual({
      type: CONNECTOR_TYPES.MQTT,
      config: {
        urls: '',
        topics: '',
      },
    })
  })
})
