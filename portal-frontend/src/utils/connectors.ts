import { set } from 'react-hook-form'

import { CONNECTOR_INPUTS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { ConnectorDraft, ConnectorType, HttpParameterForm, normalizeHttpBaseUrl } from '@/types/connectors'

type ConnectorConfig = NonNullable<ConnectorDraft['configuration']>

export const getConnectorDefaults = (type: ConnectorType): ConnectorConfig => {
  const defaults = {}
  CONNECTOR_INPUTS[type].forEach(field => set(defaults, field.key, field.defaultValue))
  return defaults as ConnectorConfig
}

export const getConnectorFormData = (
  connectorType: ConnectorType,
  currentConfig: ConnectorDraft['configuration'] | undefined,
): ConnectorDraft['configuration'] => {
  return {
    ...getConnectorDefaults(connectorType),
    ...(currentConfig ?? {}),
  }
}

export const formatPathWithQuery = (path: string, queryParameters: HttpParameterForm[]): string => {
  const namedParameters = queryParameters.filter(parameter => parameter.name)
  const pairs = namedParameters.map(({ name, value }) => `${name}=${value}`)
  const query = pairs.join('&')
  return query ? `${path}?${query}` : path
}

export const buildApiCallUrl = (baseUrl: string, path: string, queryParameters: HttpParameterForm[]): string => {
  const encodedParameters = queryParameters.map(({ name, value }) => ({
    name: encodeURIComponent(name),
    value: encodeURIComponent(value),
  }))
  return `${normalizeHttpBaseUrl(baseUrl)}${formatPathWithQuery(path, encodedParameters)}`
}
