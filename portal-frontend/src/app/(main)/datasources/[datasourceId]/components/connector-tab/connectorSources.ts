import { CONNECTOR_TYPES } from '@/const/connectors'
import { ConnectorType } from '@/types/connectors'
import { ConnectorField } from '@/types/datasources'

export const CONNECTOR_INPUTS: Record<ConnectorType, ConnectorField[]> = {
  [CONNECTOR_TYPES.MQTT]: [
    {
      key: 'urls',
      type: 'textArea',
      label: { label: 'Brokers', labelHint: 'commaSeparated' },
      rows: 3,
      placeholder: 'tcp://localhost:1883',
      defaultValue: '',
      required: true,
    },
    {
      key: 'user',
      type: 'input',
      label: { label: 'User', labelHint: null },
      placeholder: '',
      defaultValue: '',
      required: false,
    },
    {
      key: 'password',
      type: 'input',
      label: { label: 'Password', labelHint: null },
      placeholder: '',
      defaultValue: '',
      required: false,
    },
    {
      key: 'topics',
      type: 'input',
      label: { label: 'Topics', labelHint: 'commaSeparated' },
      placeholder: 'foo/bar, sensors/#',
      defaultValue: '',
      required: true,
    },
    {
      key: 'client_id',
      type: 'input',
      label: { label: 'Client ID', labelHint: null },
      placeholder: 'optional',
      defaultValue: '',
      required: false,
    },
    {
      key: 'qos',
      type: 'select',
      label: { label: 'QoS', labelHint: null },
      options: ['0', '1', '2'],
      defaultValue: '1',
      required: true,
    },
    {
      key: 'connect_timeout',
      type: 'input',
      expert: true,
      label: { label: 'Connect Timeout', labelHint: null },
      placeholder: '3s',
      defaultValue: '3s',
      required: false,
    },
    {
      key: 'keepalive',
      type: 'input',
      expert: true,
      label: { label: 'Keepalive', labelHint: null },
      placeholder: '30s',
      defaultValue: '30s',
      required: false,
    },
    {
      key: 'tls',
      type: 'checkbox',
      expert: true,
      label: { label: 'TLS Enabled', labelHint: null },
      defaultValue: false,
      required: false,
    },
  ],

  [CONNECTOR_TYPES.SQL]: [
    {
      key: 'driver',
      type: 'select',
      label: { label: 'Driver', labelHint: '' },
      // Only postgres is supported by the pipeline engine (the backend rejects other drivers).
      options: ['postgres'],
      defaultValue: 'postgres',
      required: true,
    },
    {
      key: 'dsn',
      type: 'input',
      label: { label: 'DSN', labelHint: null },
      placeholder: 'Driver-spezifischer Connection String',
      defaultValue: '',
      required: true,
    },
    {
      key: 'user',
      type: 'input',
      label: { label: 'User', labelHint: null },
      placeholder: '',
      defaultValue: '',
      required: false,
    },
    {
      key: 'password',
      type: 'input',
      label: { label: 'Password', labelHint: null },
      placeholder: '',
      defaultValue: '',
      required: false,
    },
    {
      key: 'table',
      type: 'input',
      label: { label: 'Table', labelHint: null },
      placeholder: 'table',
      defaultValue: '',
      required: true,
    },
    {
      key: 'columns',
      type: 'textArea',
      label: { label: 'Columns', labelHint: 'commaSeparated' },
      rows: 3,
      placeholder: '* oder col1, col2',
      defaultValue: '*',
      required: true,
    },
    {
      key: 'where',
      type: 'input',
      label: { label: 'WHERE', labelHint: null },
      placeholder: 'col > 10 AND active',
      defaultValue: '',
      required: false,
    },
    // Removed (issue #1779): prefix/suffix/init_statement and conn_max_* are Redpanda-Connect
    // connector options the NiFi SQL source does not honor (the pool is platform-managed). Only the
    // fields above are actually processed.
  ],
}
