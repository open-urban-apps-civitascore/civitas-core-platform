import { CONNECTOR_TYPES } from '@/const/connectors'
import {
  ConnectorApiConfig,
  ConnectorApiData,
  ConnectorDraft,
  MqttApiConfig,
  MqttLooseConfig,
  SqlApiConfig,
  SqlLooseConfig,
} from '@/types/connectors'

import {
  getConnectorDefaults,
  getConnectorFormData,
  getInitialConnectorFormData,
  mapConnectorFormToApiData,
  mapMqttApiDataToForm,
  mapMqttFormToApiData,
  mapSqlApiDataToForm,
  mapSqlFormToApiData,
} from './connectors'

vi.mock('@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources', () => ({
  CONNECTORS: {
    mqtt: {
      properties: [
        { key: 'urls', defaultValue: [] },
        { key: 'topics', defaultValue: [] },
      ],
    },
    sql: {
      properties: [
        { key: 'columns', defaultValue: [] },
        { key: 'init_files', defaultValue: [] },
      ],
    },
  },
}))

describe('getConnectorDefaults', () => {
  it('returns mqtt defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.MQTT)
    expect(result).toEqual<ConnectorApiConfig['mqtt']>({
      urls: [],
      topics: [],
    })
  })

  it('returns sql defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.SQL)
    expect(result).toEqual<ConnectorApiConfig['sql']>({
      columns: [],
      init_files: [],
    })
  })
})

describe('MQTT mappers', () => {
  it('mapMqttApiDataToForm maps api data to form data', () => {
    const api: MqttApiConfig = { urls: ['a', 'b'], topics: ['t1'] }
    const form = mapMqttApiDataToForm(api)
    expect(form).toEqual<MqttLooseConfig>({
      urls: 'a,b',
      topics: 't1',
    })
  })

  it('mapMqttFormToApiData maps form data to api data', () => {
    const form: MqttLooseConfig = { urls: 'a,b', topics: 't1' }
    const api = mapMqttFormToApiData(form)
    expect(api).toEqual<MqttApiConfig>({
      urls: ['a', 'b'],
      topics: ['t1'],
    })
  })
})

describe('SQL mappers', () => {
  it('mapSqlApiDataToForm maps api data to form data', () => {
    const api: SqlApiConfig = { columns: ['c1'], init_files: ['f1', 'f2'] }
    const form = mapSqlApiDataToForm(api)
    expect(form).toEqual<SqlLooseConfig>({
      columns: 'c1',
      init_files: 'f1,f2',
    })
  })

  it('mapSqlFormToApiData maps form data to api data', () => {
    const form: SqlLooseConfig = { columns: 'c1', init_files: 'f1,f2' }
    const api = mapSqlFormToApiData(form)
    expect(api).toEqual<SqlApiConfig>({
      columns: ['c1'],
      init_files: ['f1', 'f2'],
    })
  })
})

describe('getInitialConnectorFormData', () => {
  it('returns datasource config if it exists for selected type', () => {
    const apiConnector = { type: 'sql', config: { columns: ['c1'], init_files: ['f1', 'f2'] } }

    const result = getInitialConnectorFormData(apiConnector as ConnectorApiData)
    expect(result.type).toBe('sql')
    expect(result.config).toEqual<SqlLooseConfig>({
      columns: 'c1',
      init_files: 'f1,f2',
    })
  })

  it('returns mqtt config as default', () => {
    const result = getInitialConnectorFormData(null)
    expect(result.type).toBe('mqtt')
    expect(result.config).toEqual<MqttLooseConfig>({
      urls: '',
      topics: '',
    })
  })

  describe('getConnectorFormData', () => {
    it('uses initial datasource config if it exists for selected type', () => {
      const initialValues: ConnectorDraft = {
        type: CONNECTOR_TYPES.SQL,
        config: { columns: 'a', init_files: '' },
      }
      const result = getConnectorFormData(CONNECTOR_TYPES.SQL, initialValues)
      expect(result).toEqual(initialValues)
    })
    it('sets default values if no initial datasource values for selected tyoe', () => {
      const result = getConnectorFormData(CONNECTOR_TYPES.SQL, null)
      expect(result).toEqual({
        type: CONNECTOR_TYPES.SQL,
        config: { columns: '', init_files: '' },
      })
    })
  })

  describe('mapConnectorFormToApiData', () => {
    it('maps form data to api data', () => {
      const draft: ConnectorDraft = {
        type: CONNECTOR_TYPES.SQL,
        config: { columns: 'a,b', init_files: '' },
      }
      const result = mapConnectorFormToApiData(draft)
      expect(result).toEqual({
        type: CONNECTOR_TYPES.SQL,
        config: {
          columns: ['a', 'b'],
          init_files: [],
        },
      })
    })
  })
})
