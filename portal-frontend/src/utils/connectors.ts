import { get, set } from 'react-hook-form'

import { CONNECTOR_INPUTS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { CONNECTOR_TYPES, HTTP_AUTHENTICATION_TYPES, SECRET_PLACEHOLDER } from '@/const/connectors'
import {
  ConnectorDraft,
  ConnectorType,
  HttpAuthenticationType,
  HttpConnectionForm,
  normalizeHttpBaseUrl,
  parseStringArray,
} from '@/types/connectors'
import { HttpParameterForm } from '@/types/httpApiCalls'
import { isEmptyParameter } from '@/utils/connectorMappers'

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

const SECRET_FIELDS: Record<HttpAuthenticationType, readonly string[]> = {
  [HTTP_AUTHENTICATION_TYPES.NONE]: [],
  [HTTP_AUTHENTICATION_TYPES.BASIC]: ['password'],
  [HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN]: ['token'],
  [HTTP_AUTHENTICATION_TYPES.API_TOKEN]: ['value'],
  [HTTP_AUTHENTICATION_TYPES.OAUTH2]: ['clientSecret'],
  [HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE]: ['certificate', 'privateKey'],
}

const SECRET_FIELD_NAMES = Object.values(SECRET_FIELDS).flat()

const HTTP_SECRET_PATHS = SECRET_FIELD_NAMES.map(field => `authentication.${field}`)

export const CONNECTOR_SECRET_PATHS: Record<ConnectorType, readonly string[]> = {
  [CONNECTOR_TYPES.HTTP]: HTTP_SECRET_PATHS,
  [CONNECTOR_TYPES.MQTT]: ['password'],
  [CONNECTOR_TYPES.SQL]: ['password'],
}

type ConnectorConfiguration = Record<string, unknown>

// The backend compares a SQL DSN without this prefix
const JDBC_PREFIX = /^jdbc:/i

// Scheme, host and port as written
const URL_DESTINATION = /^([^:/?#]+):\/\/(?:[^@/?#]*@)?(\[[^\]]*\]|[^:/?#]*)(?::(\d*))?(?:[/?#]|$)/

const URL_QUERY = /^[^?#]*\?([^#]*)/

// The backend counts the sslmode of the query as part of the destination
const SSLMODE_NAME = 'sslmode'

const getSslmode = (url: string) => {
  const [, query] = URL_QUERY.exec(url) ?? []
  for (const parameter of query?.split('&') ?? []) {
    const [name, ...value] = parameter.split('=')
    if (name === SSLMODE_NAME) return value.join('=')
  }
  return undefined
}

const toDestination = (url: string) => {
  const [, scheme, host, port] = URL_DESTINATION.exec(url) ?? []
  if (!scheme || !host) return url
  const sslmode = getSslmode(url)
  const sslmodeSuffix = sslmode === undefined ? '' : ` ${SSLMODE_NAME}=${sslmode}`
  return `${scheme}://${host}:${port ?? ''}${sslmodeSuffix}`
}

const getHttpAddresses = (configuration: HttpConnectionForm) => {
  const { baseUrl, authentication } = configuration
  const tokenUrl = authentication?.type === HTTP_AUTHENTICATION_TYPES.OAUTH2 ? authentication.tokenUrl : undefined
  return [normalizeHttpBaseUrl(baseUrl ?? ''), (tokenUrl ?? '').trim()]
}

const getAddresses = (connectorType: ConnectorType, configuration: ConnectorConfiguration) => {
  switch (connectorType) {
    case CONNECTOR_TYPES.HTTP:
      return getHttpAddresses(configuration as HttpConnectionForm)
    case CONNECTOR_TYPES.MQTT:
      return parseStringArray(configuration.urls) ?? []
    case CONNECTOR_TYPES.SQL:
      return [typeof configuration.dsn === 'string' ? configuration.dsn.trim().replace(JDBC_PREFIX, '') : '']
  }
}

const getDestinations = (connectorType: ConnectorType, configuration: ConnectorConfiguration) => {
  const presentAddresses = getAddresses(connectorType, configuration).filter(Boolean)
  return presentAddresses.map(toDestination)
}

export const getSecretPaths = (connectorType: ConnectorType, configuration: ConnectorConfiguration) => {
  if (connectorType !== CONNECTOR_TYPES.HTTP) return CONNECTOR_SECRET_PATHS[connectorType]
  const { authentication } = configuration as HttpConnectionForm
  const secretFields = authentication ? SECRET_FIELDS[authentication.type] : []
  return secretFields.map(field => `authentication.${field}`)
}

type ConnectorValues = { connectorType?: ConnectorType; configuration?: ConnectorConfiguration }

export const findSecretsToReenter = (current: ConnectorValues, loaded: ConnectorValues | undefined) => {
  const { connectorType, configuration } = current
  const loadedConfiguration = loaded?.connectorType === connectorType ? loaded?.configuration : undefined
  if (!connectorType || !configuration || !loadedConfiguration) return []
  const maskedSecretPaths = getSecretPaths(connectorType, configuration).filter(
    path => get(configuration, path) === SECRET_PLACEHOLDER,
  )
  if (!maskedSecretPaths.length) return []
  const loadedDestinations = new Set(getDestinations(connectorType, loadedConfiguration))
  const currentDestinations = getDestinations(connectorType, configuration)
  const isAtLoadedDestinations = currentDestinations.every(destination => loadedDestinations.has(destination))
  return isAtLoadedDestinations ? [] : maskedSecretPaths
}

const FIELD_VALUE_TYPES = new Set(['string', 'number', 'boolean'])

const SENT_PARAMETER_PATH = /^(apiCalls\.\d+\.(?:queryParameters|headers))\.(\d+)(.*)$/

// The backend counts only the sent rows; empty rows are not sent
const toFormRowPath = (path: string, configuration: unknown) => {
  const [, listPath, sentIndex, rest] = SENT_PARAMETER_PATH.exec(path) ?? []
  if (!listPath) return path
  const parameters: HttpParameterForm[] = get(configuration, listPath) ?? []
  const sentRowIndexes = parameters.flatMap((parameter, index) => (isEmptyParameter(parameter) ? [] : [index]))
  const rowIndex = sentRowIndexes[Number(sentIndex)]
  return rowIndex === undefined ? undefined : `${listPath}.${rowIndex}${rest}`
}

const isFieldValuePath = (path: string, configuration: unknown) => {
  const parentPath = path.slice(0, Math.max(path.lastIndexOf('.'), 0))
  const parent: unknown = parentPath ? get(configuration, parentPath) : configuration
  const value: unknown = get(configuration, path)
  return typeof parent === 'object' && parent !== null && FIELD_VALUE_TYPES.has(typeof value)
}

export const toFormConnectorPath = (path: string, configuration: unknown) => {
  const formPath = toFormRowPath(path, configuration)
  return formPath && isFieldValuePath(formPath, configuration) ? formPath : undefined
}
