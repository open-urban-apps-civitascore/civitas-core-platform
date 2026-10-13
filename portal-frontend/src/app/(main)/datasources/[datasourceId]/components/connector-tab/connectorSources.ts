import { CONNECTOR_TYPES, HTTP_AUTHENTICATION_TYPES } from '@/const/connectors'
import { ConnectorType } from '@/types/connectors'
import { ConnectorField } from '@/types/datasources'

export const CONNECTOR_INPUTS: Record<ConnectorType, ConnectorField[]> = {
  [CONNECTOR_TYPES.MQTT]: [
    {
      key: 'urls',
      type: 'textArea',
      label: { labelKey: 'mqtt.urls.label', hintKey: 'mqtt.urls.hint' },
      rows: 3,
      placeholder: 'tcp://localhost:1883',
      defaultValue: '',
      required: true,
    },
    {
      key: 'protocol_version',
      type: 'select',
      label: { labelKey: 'mqtt.protocolVersion.label' },
      options: ['3', '5'],
      placeholderKey: 'mqtt.protocolVersion.placeholder',
      defaultValue: '3',
      required: true,
    },
    {
      key: 'user',
      type: 'input',
      label: { labelKey: 'mqtt.user.label' },
      defaultValue: '',
      required: false,
    },
    {
      key: 'password',
      type: 'input',
      inputType: 'password',
      label: { labelKey: 'mqtt.password.label' },
      defaultValue: '',
      required: false,
    },
    {
      key: 'topics',
      type: 'input',
      label: { labelKey: 'mqtt.topics.label' },
      placeholder: 'foo/bar',
      defaultValue: '',
      required: true,
    },
    {
      key: 'qos',
      type: 'select',
      label: { labelKey: 'mqtt.qos.label' },
      options: ['0', '1', '2'],
      defaultValue: '1',
      required: true,
    },
    {
      key: 'connect_timeout',
      type: 'input',
      expert: true,
      label: { labelKey: 'mqtt.connectTimeout.label' },
      placeholder: '3s',
      defaultValue: '3s',
      required: false,
    },
    {
      key: 'keepalive',
      type: 'input',
      expert: true,
      label: { labelKey: 'mqtt.keepalive.label' },
      placeholder: '30s',
      defaultValue: '30s',
      required: false,
    },
    {
      key: 'tls',
      type: 'checkbox',
      expert: true,
      label: { labelKey: 'mqtt.tls.label' },
      defaultValue: false,
      required: false,
    },
  ],

  [CONNECTOR_TYPES.SQL]: [
    {
      key: 'driver',
      type: 'select',
      label: { labelKey: 'sql.driver.label' },
      // Only postgres is wired in the pipeline engine; the adapter rejects any other driver at
      // deploy, so the form offers just this one.
      options: ['postgres'],
      defaultValue: 'postgres',
      required: true,
    },
    {
      // Intentionally a plain (unmasked) input: the DSN is a readable connection
      // string, not a secret. Credentials belong in the dedicated `user`/`password`
      // fields below, not embedded in the DSN — so it is not declared inputType: 'password'.
      key: 'dsn',
      type: 'textArea',
      label: { labelKey: 'sql.dsn.label' },
      placeholderKey: 'sql.dsn.placeholder',
      defaultValue: '',
      required: true,
    },
    {
      key: 'user',
      type: 'input',
      label: { labelKey: 'sql.user.label' },
      defaultValue: '',
      required: false,
    },
    {
      key: 'password',
      type: 'input',
      inputType: 'password',
      label: { labelKey: 'sql.password.label' },
      defaultValue: '',
      required: false,
    },
    {
      key: 'table',
      type: 'input',
      label: { labelKey: 'sql.table.label' },
      placeholderKey: 'sql.table.placeholder',
      defaultValue: '',
      required: true,
    },
    {
      key: 'columns',
      type: 'textArea',
      label: { labelKey: 'sql.columns.label', hintKey: 'sql.columns.hint' },
      rows: 3,
      placeholderKey: 'sql.columns.placeholder',
      defaultValue: '*',
      required: true,
    },
    {
      key: 'where',
      type: 'input',
      label: { labelKey: 'sql.where.label' },
      placeholder: 'col > 10 AND active',
      defaultValue: '',
      required: false,
    },
  ],

  [CONNECTOR_TYPES.HTTP]: [
    {
      key: 'baseUrl',
      type: 'input',
      label: { labelKey: 'http.baseUrl.label' },
      placeholder: 'https://api.example.org/v1',
      defaultValue: '',
      required: true,
    },
    {
      key: 'authentication.type',
      type: 'select',
      label: { labelKey: 'http.authentication.label' },
      options: [
        { value: HTTP_AUTHENTICATION_TYPES.NONE, labelKey: 'http.authentication.none' },
        { value: HTTP_AUTHENTICATION_TYPES.BASIC, labelKey: 'http.authentication.basic' },
      ],
      defaultValue: HTTP_AUTHENTICATION_TYPES.NONE,
      required: true,
    },
    {
      key: 'authentication.username',
      type: 'input',
      label: { labelKey: 'http.username.label' },
      visibleWhen: { key: 'authentication.type', value: HTTP_AUTHENTICATION_TYPES.BASIC },
      defaultValue: '',
      required: true,
    },
    {
      key: 'authentication.password',
      type: 'input',
      inputType: 'password',
      label: { labelKey: 'http.password.label' },
      visibleWhen: { key: 'authentication.type', value: HTTP_AUTHENTICATION_TYPES.BASIC },
      defaultValue: '',
      required: true,
    },
  ],
}
