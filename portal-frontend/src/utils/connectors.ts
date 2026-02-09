import { CONNECTORS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { ConnectorDraft, ConnectorType } from '@/types/connectors'

export const getConnectorDefaults = (type: ConnectorType): ConnectorDraft['config'] => {
  const defaults = CONNECTORS[type].properties.reduce(
    (acc, property) => {
      acc[property.key as keyof ConnectorDraft['config']] =
        property.defaultValue as ConnectorDraft['config'][keyof ConnectorDraft['config']]
      return acc
    },
    {} as ConnectorDraft['config'],
  )
  console.log('getConnectorDefaults', defaults)
  return defaults
}

export const getConnectorFormData = <T extends ConnectorType>(
  type: T,
  connectorFormData: ConnectorDraft | null,
): ConnectorDraft => {
  const data = connectorFormData?.type === type ? connectorFormData : { type, config: getConnectorDefaults(type) }
  console.log('getConnectorFormData', data)
  return connectorFormData?.type === type ? connectorFormData : { type, config: getConnectorDefaults(type) }
}
