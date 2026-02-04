/* eslint-disable @typescript-eslint/naming-convention */
import { CONNECTORS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { CONNECTOR_TYPES } from '@/const/connectors'
import {
  ConnectorApiConfig,
  ConnectorApiData,
  ConnectorDraft,
  ConnectorType,
  MqttApiConfig,
  MqttLooseConfig,
  SqlApiConfig,
  SqlLooseConfig,
} from '@/types/connectors'

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

export const mapMqttApiDataToForm = (config: MqttApiConfig): MqttLooseConfig => {
  return {
    ...config,
    urls: config.urls.join(',') || '',
    topics: config.topics.join(',') || '',
  }
}

export const mapSqlApiDataToForm = (config: SqlApiConfig): SqlLooseConfig => {
  return {
    ...config,
    columns: config.columns.join(',') || '',
    init_files: config.init_files.join(',') || '',
  }
}
export const mapMqttFormToApiData = (config: MqttLooseConfig): MqttApiConfig => {
  return {
    ...config,
    urls: config.urls?.split(',') || [],
    topics: config.topics?.split(',') || [],
  }
}

export const mapSqlFormToApiData = (config: SqlLooseConfig): SqlApiConfig => {
  return {
    ...config,
    columns: config.columns?.split(',') || [],
    init_files: config.init_files?.split(',') || [],
  }
}

export const getInitialConnectorFormData = (connector: ConnectorApiData | null) => {
  if (connector?.type === 'sql') {
    return {
      type: connector?.type,
      config: mapSqlApiDataToForm(connector?.config || getConnectorDefaults(CONNECTOR_TYPES.SQL)),
    } as const
  }
  return {
    type: connector?.type || 'mqtt',
    config: mapMqttApiDataToForm(connector?.config || getConnectorDefaults(CONNECTOR_TYPES.MQTT)),
  } as const
}

export const getConnectorFormData = (type: ConnectorType, connectorFormData: ConnectorDraft | null) => {
  switch (type) {
    case CONNECTOR_TYPES.SQL:
      return {
        type: type,
        config:
          connectorFormData?.type === type
            ? connectorFormData.config
            : mapSqlApiDataToForm(getConnectorDefaults(CONNECTOR_TYPES.SQL)),
      }
    case CONNECTOR_TYPES.MQTT:
      return {
        type: type,
        config:
          connectorFormData?.type === type
            ? connectorFormData.config
            : mapMqttApiDataToForm(getConnectorDefaults(CONNECTOR_TYPES.MQTT)),
      }
  }
}

export const mapConnectorConfigToApiData = (connectorFormData: ConnectorDraft) => {
  switch (connectorFormData.type) {
    case CONNECTOR_TYPES.SQL:
      return {
        type: connectorFormData.type,
        config: mapSqlFormToApiData(connectorFormData?.config),
      }
    case CONNECTOR_TYPES.MQTT:
      return {
        type: connectorFormData.type,
        config: mapMqttFormToApiData(connectorFormData?.config),
      }
  }
}
