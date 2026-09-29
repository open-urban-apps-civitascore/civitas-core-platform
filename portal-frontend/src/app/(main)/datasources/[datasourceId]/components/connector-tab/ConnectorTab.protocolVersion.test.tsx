import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { useForm, UseFormReturn } from 'react-hook-form'
import { describe, expect, it } from 'vitest'

import { Form } from '@/components/ui/form'
import type { DatasourceFormDraft } from '@/types/datasources'
import { DatasourceFormDraftSchema } from '@/types/datasources'
import { getConnectorDefaults } from '@/utils/connectors'

import { ConnectorTab } from './ConnectorTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const PROTOCOL_VERSION_TRIGGER_TESTID = 'configuration.protocol_versionSelectTrigger'
const PROTOCOL_VERSION_CONTENT_TESTID = 'configuration.protocol_versionSelectContent'

const baseMqttConfiguration = {
  urls: '',
  protocol_version: '3',
  topics: '',
  qos: '1',
  connect_timeout: '',
  keepalive: '',
  tls: false,
  user: '',
  password: '',
}

const renderConnectorTab = (values?: Partial<DatasourceFormDraft>) => {
  const defaultValues: DatasourceFormDraft = {
    id: '1',
    name: '',
    description: '',
    dataSourceStatus: 'DRAFT',
    dataStructureVersionId: null,
    connectorType: 'MQTT',
    configuration: baseMqttConfiguration,
    ...values,
  }

  let capturedForm: UseFormReturn<DatasourceFormDraft>

  const Wrapper = () => {
    const form = useForm<DatasourceFormDraft>({
      resolver: zodResolver(DatasourceFormDraftSchema),
      defaultValues,
    })
    capturedForm = form

    return (
      <Form {...form}>
        <ConnectorTab form={form} connectorType={defaultValues.connectorType} />
      </Form>
    )
  }

  const view = render(<Wrapper />)
  return { ...view, form: capturedForm! }
}

describe('MQTT protocol version field (acceptance criteria)', () => {
  it('shows an MQTT-version combobox for MQTT data sources', () => {
    renderConnectorTab({ configuration: getConnectorDefaults('MQTT') })

    const trigger = screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID)
    expect(trigger).toBeInTheDocument()
    expect(trigger).toHaveAttribute('role', 'combobox')
    expect(screen.getByLabelText('mqtt.protocolVersion.label')).toBe(trigger)
  })

  it('does not show a protocol version field for a non-MQTT connector', () => {
    renderConnectorTab({
      connectorType: 'SQL',
      configuration: { driver: 'postgres', dsn: '', table: '', columns: '' },
    })

    expect(screen.queryByTestId(PROTOCOL_VERSION_TRIGGER_TESTID)).not.toBeInTheDocument()
  })

  it('offers MQTT 3 and MQTT 5 as the available protocol versions', async () => {
    renderConnectorTab({ configuration: getConnectorDefaults('MQTT') })

    fireEvent.click(screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID))
    const content = await screen.findByTestId(PROTOCOL_VERSION_CONTENT_TESTID)

    expect(within(content).getByTestId('configuration.protocol_versionSelectItem0')).toHaveTextContent('3')
    expect(within(content).getByTestId('configuration.protocol_versionSelectItem1')).toHaveTextContent('5')

    expect(within(content).getAllByRole('option')).toHaveLength(3)
  })

  it('stores the selected protocol version in the datasource configuration', () => {
    const { form } = renderConnectorTab({ configuration: getConnectorDefaults('MQTT') })

    fireEvent.click(screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID))
    fireEvent.click(screen.getByTestId('configuration.protocol_versionSelectItem1'))

    expect(form.getValues('configuration.protocol_version')).toBe('5')
  })

  it('pre-selects the stored protocol version when editing a datasource stored as MQTT 5', () => {
    renderConnectorTab({ configuration: { ...getConnectorDefaults('MQTT'), protocol_version: '5' } })

    expect(screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID)).toHaveTextContent('5')
  })

  it('pre-selects the stored protocol version when editing a datasource stored as MQTT 3', () => {
    renderConnectorTab({ configuration: { ...getConnectorDefaults('MQTT'), protocol_version: '3' } })

    expect(screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID)).toHaveTextContent('3')
  })

  it('defaults the dropdown to MQTT 3 for a newly created MQTT connector', () => {
    renderConnectorTab({ configuration: getConnectorDefaults('MQTT') })

    expect(screen.getByTestId(PROTOCOL_VERSION_TRIGGER_TESTID)).toHaveTextContent('3')
  })
})
