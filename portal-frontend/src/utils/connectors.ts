import { CONNECTORS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { ConnectorApiConfig, ConnectorApiData, ConnectorDraft, ConnectorType } from '@/types/connectors'

const getArrayFromString = (value: string | undefined) =>
  !!value
    ? value
        ?.split(',')
        .map(v => v.trim())
        .filter(Boolean)
    : []

export const getConnectorDefaults = <T extends ConnectorType>(type: T): ConnectorApiConfig[T] => {
  return CONNECTORS[type].properties.reduce(
    (acc, property) => {
      acc[property.key as keyof ConnectorApiConfig[T]] =
        property.defaultValue as ConnectorApiConfig[T][keyof ConnectorApiConfig[T]]
      return acc
    },
    {} as ConnectorApiConfig[T],
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

export const mapFormToApi = <T extends Record<string, unknown>, K extends readonly (keyof T)[]>(
  config: T,
  fields: K,
): Omit<T, K[number]> & { [P in K[number]]: string[] } => {
  const result = { ...config } as Record<string, unknown>

  Object.entries(result).forEach(([key, value]) => {
    if (typeof value === 'string') {
      result[key] = value.trim()
    }
  })

  fields.forEach(key => {
    result[key as string] = getArrayFromString(config[key] as string)
  })

  return result as Omit<T, K[number]> & { [P in K[number]]: string[] }
}

export const getInitialConnectorFormData = (connector: ConnectorApiData | null): ConnectorDraft => {
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

export const mapConnectorFormToApiData = (connectorFormData: ConnectorDraft): ConnectorApiData => {
  switch (connectorFormData.type) {
    case CONNECTOR_TYPES.SQL:
      return {
        type: connectorFormData.type,
        config: mapFormToApi(connectorFormData?.config, ['columns', 'init_files']),
      }
    case CONNECTOR_TYPES.MQTT:
      return {
        type: connectorFormData.type,
        config: mapFormToApi(connectorFormData?.config, ['urls', 'topics']),
      }
  }
}
