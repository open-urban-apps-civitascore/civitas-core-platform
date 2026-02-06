import { zodResolver } from '@hookform/resolvers/zod'
import { render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { fireEvent } from 'storybook/test'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import type { DatasourceFormDraft } from '@/types/datasources'
import { DatasourceFormDraftSchema } from '@/types/datasources'

import { ConnectorTab } from './ConnectorTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))
vi.mock('./connectorSources', () => ({
  CONNECTORS: {
    mqtt: {
      label: 'MQTT',
      properties: [
        {
          key: 'urls',
          type: 'input',
          placeholder: 'mqtt://localhost',
          required: true,
          label: { label: 'URLs', labelHint: 'info.hint' },
        },
        {
          key: 'tls',
          type: 'checkbox',
          placeholder: '',
          required: false,
          label: { label: 'TLS', labelHint: null },
        },
        {
          key: 'qos',
          type: 'select',
          placeholder: 'qos',
          required: false,
          options: ['0', '1', '2'],
          label: { label: 'QoS', labelHint: null },
        },
        {
          key: 'clientId',
          type: 'textArea',
          placeholder: 'client id',
          required: false,
          label: { label: 'Client ID', labelHint: null },
        },
      ],
    },
    sql: {
      label: 'SQL',
      properties: [
        {
          key: 'dsn',
          type: 'input',
          placeholder: 'postgres://user:pass@host:5432/db',
          required: true,
          label: { label: 'DSN', labelHint: null },
        },
        {
          key: 'table',
          type: 'input',
          placeholder: 'my_table',
          required: true,
          label: { label: 'Table', labelHint: null },
        },
        {
          key: 'columns',
          type: 'input',
          placeholder: 'id,name,created_at',
          required: true,
          label: { label: 'Columns', labelHint: null },
        },
        {
          key: 'init_files',
          type: 'input',
          placeholder: 'init.sql',
          required: false,
          label: { label: 'Init Files', labelHint: null },
        },
      ],
    },
  },
}))

const defaultValues: DatasourceFormDraft = {
  id: '1',
  name: '',
  description: '',
  tags: [],
  status: 'draft',
  connector: {
    type: 'mqtt',
    config: {
      urls: '',
      topics: '',
      clientId: '',
      qos: '0',
      connect_timeout: '',
      keepalive: '',
      tls: false,
    },
  },
}

const renderConnectorTab = (isDraftMode = false, values?: Partial<DatasourceFormDraft>) => {
  const Wrapper = () => {
    const form = useForm<DatasourceFormDraft>({
      resolver: zodResolver(DatasourceFormDraftSchema),
      defaultValues: { ...defaultValues, ...values },
    })

    return (
      <Form {...form}>
        <ConnectorTab form={form} isDraftMode={isDraftMode} />
      </Form>
    )
  }

  return render(<Wrapper />)
}

describe('ConnectorTab (integration)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders connector type select', async () => {
    renderConnectorTab()

    const select = screen.getByTestId('connector.typeSelectTrigger')
    expect(screen.getByLabelText('type')).toBeInTheDocument()
    expect(screen.getByText('MQTT')).toBeInTheDocument()
    fireEvent.click(select)
    await screen.findByText('SQL')
    expect(screen.getByText('SQL')).toBeInTheDocument()
  })

  it('does not render config when no connector type selected', async () => {
    renderConnectorTab(true, { connector: null })

    expect(screen.getByLabelText('type')).toBeInTheDocument()
    expect(screen.queryByText('MQTT')).not.toBeInTheDocument()
    expect(screen.queryByText('SQL')).not.toBeInTheDocument()
    expect(screen.queryByText('title2')).not.toBeInTheDocument()
  })

  it('renders dynamic fields from config', () => {
    renderConnectorTab()

    expect(screen.getByLabelText(/URLs/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeInTheDocument()
    expect(screen.getByLabelText(/QoS/)).toBeInTheDocument()
    expect(screen.getByLabelText('Client ID')).toBeInTheDocument()
  })

  it('renders label hint when present', () => {
    renderConnectorTab()

    expect(screen.getByText(/(info.hint)/)).toBeInTheDocument()
  })

  it('works in draft mode', () => {
    renderConnectorTab(true)

    expect(screen.getByLabelText(/URLs/)).toBeInTheDocument()
  })
})
