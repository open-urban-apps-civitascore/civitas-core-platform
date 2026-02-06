import { CONNECTORS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { ConnectorApiResponseConfig, ConnectorApiResponseData, ConnectorDraft, ConnectorType } from '@/types/connectors'

export const getConnectorDefaults = <T extends ConnectorType>(type: T): ConnectorApiResponseConfig[T] => {
  return CONNECTORS[type].properties.reduce(
    (acc, property) => {
      acc[property.key as keyof ConnectorApiResponseConfig[T]] =
        property.defaultValue as ConnectorApiResponseConfig[T][keyof ConnectorApiResponseConfig[T]]
      return acc
    },
    {} as ConnectorApiResponseConfig[T],
  )
}

export const mapApiToForm = <T extends Record<string, unknown>, K extends readonly (keyof T)[]>(
  config: T,
  fields: K,
): Omit<T, K[number]> & {
  [P in K[number]]: string
} => {
  const result = { ...config } as Record<string, unknown>

  fields.forEach(key => {
    result[key as string] = Array.isArray(config[key]) ? (config[key] as string[]).join(',') : ''
  })

  return result as Omit<T, K[number]> & {
    [P in K[number]]: string
  }
}

export const getInitialConnectorFormData = (connector: ConnectorApiResponseData | null): ConnectorDraft => {
  switch (connector?.type) {
    case CONNECTOR_TYPES.SQL:
      return {
        type: connector?.type,
        config: mapApiToForm(connector?.config, ['columns', 'init_files']),
      }
    case CONNECTOR_TYPES.MQTT:
    default:
      return {
        type: connector?.type || 'mqtt',
        config: mapApiToForm(connector?.config || getConnectorDefaults(CONNECTOR_TYPES.MQTT), ['urls', 'topics']),
      }
  }
}

export const getConnectorFormData = (type: ConnectorType, connectorFormData: ConnectorDraft | null): ConnectorDraft => {
  switch (type) {
    case CONNECTOR_TYPES.SQL:
      return {
        type: type,
        config:
          connectorFormData?.type === type
            ? connectorFormData.config
            : mapApiToForm(getConnectorDefaults(CONNECTOR_TYPES.SQL), ['columns', 'init_files']),
      }
    case CONNECTOR_TYPES.MQTT:
      return {
        type: type,
        config:
          connectorFormData?.type === type
            ? connectorFormData.config
            : mapApiToForm(getConnectorDefaults(CONNECTOR_TYPES.MQTT), ['urls', 'topics']),
      }
  }
}
