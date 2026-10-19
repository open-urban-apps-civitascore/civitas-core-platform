import { zodResolver } from '@hookform/resolvers/zod'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import type { DatasourceFormDraft } from '@/types/datasources'
import { DatasourceFormDraftSchema } from '@/types/datasources'

import { ConnectorTab } from './ConnectorTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))
vi.mock('./connectorSources', () => ({
  CONNECTOR_INPUTS: {
    MQTT: [
      {
        key: 'urls',
        type: 'input',
        // Literal placeholder — exercises the non-translated path.
        placeholder: 'mqtt://localhost',
        required: true,
        label: { labelKey: 'URLs', hintKey: 'hint' },
      },
      {
        key: 'tls',
        type: 'checkbox',
        required: false,
        label: { labelKey: 'TLS' },
      },
      {
        key: 'qos',
        type: 'select',
        placeholder: 'qos',
        required: false,
        options: ['0', '1', '2'],
        label: { labelKey: 'QoS' },
      },
      {
        key: 'protocol_version',
        type: 'select',
        placeholder: 'protocol version',
        required: true,
        options: ['3', '5'],
        label: { labelKey: 'Protocol Version' },
      },
      {
        key: 'keepalive',
        type: 'textArea',
        // Translated placeholder — exercises the translation-key path.
        placeholderKey: 'keepalive',
        required: false,
        label: { labelKey: 'Keepalive' },
      },
    ],
    SQL: [
      {
        key: 'dsn',
        type: 'input',
        placeholder: 'postgres://test:test@host:5432/db',
        required: true,
        label: { labelKey: 'DSN' },
      },
      {
        key: 'table',
        type: 'input',
        placeholderKey: 'my_table',
        required: true,
        label: { labelKey: 'Table' },
      },
      {
        key: 'columns',
        type: 'input',
        placeholder: 'id,name,created_at',
        required: true,
        label: { labelKey: 'Columns' },
      },
      {
        key: 'init_files',
        type: 'input',
        placeholderKey: 'init.sql',
        required: false,
        label: { labelKey: 'Init Files' },
      },
    ],
  },
}))

const defaultValues: DatasourceFormDraft = {
  id: '1',
  name: '',
  description: '',
  dataSourceStatus: 'DRAFT',
  dataStructureVersionId: null,
  connectorType: 'MQTT',
  configuration: {
    urls: '',
    topics: '',
    qos: '0',
    protocol_version: '3',
    connect_timeout: '',
    keepalive: '',
    tls: false,
  },
}

const renderConnectorTab = (
  values?: Partial<DatasourceFormDraft>,
  isReadOnly = false,
  isDatasourceReleased = false,
) => {
  const merged = { ...defaultValues, ...values }
  const Wrapper = () => {
    const form = useForm<DatasourceFormDraft>({
      resolver: zodResolver(DatasourceFormDraftSchema),
      defaultValues: merged,
    })

    return (
      <Form {...form}>
        <ConnectorTab
          form={form}
          connectorType={merged.connectorType}
          isReadOnly={isReadOnly}
          isDatasourceReleased={isDatasourceReleased}
        />
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

    const select = screen.getByTestId('connectorTypeSelectTrigger')
    expect(screen.getByText('title1')).toBeInTheDocument()
    expect(screen.getByText('title2')).toBeInTheDocument()
    expect(screen.getByLabelText('type')).toBeInTheDocument()
    expect(screen.getByText('MQTT')).toBeInTheDocument()
    fireEvent.click(select)
    await screen.findByText('SQL')
    expect(screen.getByText('SQL')).toBeInTheDocument()
  })

  it('does not render config when no connector type selected', async () => {
    renderConnectorTab({ connectorType: undefined, configuration: undefined })

    expect(screen.getByText('title1')).toBeInTheDocument()
    expect(screen.getByLabelText('type')).toBeInTheDocument()
    expect(screen.queryByText('MQTT')).not.toBeInTheDocument()
    expect(screen.queryByText('SQL')).not.toBeInTheDocument()
    expect(screen.queryByText('title2')).not.toBeInTheDocument()
  })

  it('renders dynamic fields from config with their field metadata', async () => {
    renderConnectorTab()

    expect(screen.getByLabelText(/URLs/)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('mqtt://localhost')).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeInTheDocument()
    const qosSelect = screen.getByLabelText(/QoS/)
    expect(qosSelect).toBeInTheDocument()
    expect(screen.getByLabelText('Keepalive')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('keepalive')).toBeInTheDocument()

    fireEvent.click(qosSelect)
    await screen.findByText('1')
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('renders the protocol version select field when MQTT is selected', async () => {
    renderConnectorTab()

    const protocolVersionSelect = screen.getByLabelText(/Protocol Version/)
    expect(protocolVersionSelect).toBeInTheDocument()

    // Default configured value is '3', already shown selected in the trigger — only the other
    // option needs opening the dropdown to become visible, mirroring the qos select test above.
    fireEvent.click(protocolVersionSelect)
    await screen.findByText('5')
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('renders fields for a non-default connector type', () => {
    renderConnectorTab({
      connectorType: 'SQL',
      configuration: {
        dsn: '',
        table: '',
        columns: '',
        init_files: '',
      },
    })

    expect(screen.getByText('title2')).toBeInTheDocument()
    expect(screen.getByLabelText(/DSN/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Table/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Columns/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Init Files/)).toBeInTheDocument()
    expect(screen.queryByLabelText(/URLs/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Protocol Version/)).not.toBeInTheDocument()
  })

  it('renders label hint when present', () => {
    renderConnectorTab()

    expect(screen.getByText(/\(hint\)/)).toBeInTheDocument()
  })

  it('works in draft mode', () => {
    renderConnectorTab()

    expect(screen.getByLabelText(/URLs/)).toBeInTheDocument()
  })

  describe('MQTT TLS/broker validation refresh', () => {
    it('surfaces a broker/TLS mismatch when toggling TLS creates one, and clears it when toggling back', async () => {
      // Start from a valid combination: a TLS-scheme URL with TLS enabled — no mismatch yet.
      renderConnectorTab({
        configuration: {
          ...defaultValues.configuration,
          urls: 'mqtts://broker.local:8883',
          tls: true,
        },
      })

      const tlsCheckbox = screen.getByRole('checkbox')
      expect(screen.queryByText('datasources.errors.brokerTlsMismatch')).not.toBeInTheDocument()

      // Toggle TLS off — the broker still uses a TLS-only scheme, so this is now a real mismatch.
      fireEvent.click(tlsCheckbox)
      await waitFor(() => {
        expect(screen.getByText('datasources.errors.brokerTlsMismatch')).toBeInTheDocument()
      })

      // Toggle TLS back on — the mismatch is resolved, so the now-visible error must clear.
      fireEvent.click(tlsCheckbox)
      await waitFor(() => {
        expect(screen.queryByText('datasources.errors.brokerTlsMismatch')).not.toBeInTheDocument()
      })
    })

    it('does not surface a stale mismatch on mount for an already-mismatched stored configuration', async () => {
      // The loaded configuration is already mismatched (TLS-only scheme, TLS disabled). The
      // `isFirstRevalidationRender` guard in ConnectorTab exists specifically to stop the watch
      // effect from firing a validation trigger on the initial mount render, so the user isn't
      // shown an error before they've touched anything. Without the guard, the effect would run
      // once on mount and call `form.trigger('configuration.urls')`, surfacing this error
      // unprompted.
      renderConnectorTab({
        configuration: {
          ...defaultValues.configuration,
          urls: 'mqtts://broker.local:8883',
          tls: false,
        },
      })

      // Flush pending effects/microtasks so a would-be mount-triggered validation has a chance
      // to resolve and re-render before we assert on its absence.
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0))
      })
      expect(screen.queryByText('datasources.errors.brokerTlsMismatch')).not.toBeInTheDocument()

      // Canary: the guard must only suppress the very first run, not the whole mechanism. Toggle
      // TLS on (resolves the mismatch) and back off (reintroduces it) to prove validation still
      // refreshes normally after the guarded initial render.
      const tlsCheckbox = screen.getByRole('checkbox')
      fireEvent.click(tlsCheckbox)
      fireEvent.click(tlsCheckbox)
      await waitFor(() => {
        expect(screen.getByText('datasources.errors.brokerTlsMismatch')).toBeInTheDocument()
      })
    })
  })

  describe('Read-only mode', () => {
    it('disables connector type select when isReadOnly is true', () => {
      renderConnectorTab(undefined, true)
      expect(screen.getByTestId('connectorTypeSelectTrigger')).toBeDisabled()
    })

    it('disables dynamic form fields when isReadOnly is true', () => {
      renderConnectorTab(undefined, true)
      expect(screen.getByLabelText(/URLs/)).toBeDisabled()
      expect(screen.getByRole('checkbox')).toBeDisabled()
    })

    it('fields are enabled when isReadOnly is false', () => {
      renderConnectorTab(undefined, false)
      expect(screen.getByTestId('connectorTypeSelectTrigger')).not.toBeDisabled()
      expect(screen.getByLabelText(/URLs/)).not.toBeDisabled()
    })
  })

  describe('Released data source', () => {
    it('disables the connector type and all configuration fields in edit mode', () => {
      renderConnectorTab(undefined, false, true)
      expect(screen.getByTestId('connectorTypeSelectTrigger')).toBeDisabled()
      expect(screen.getByLabelText(/URLs/)).toBeDisabled()
      expect(screen.getByRole('checkbox')).toBeDisabled()
    })

    it('shows the released info in edit mode', () => {
      renderConnectorTab(undefined, false, true)
      expect(screen.getByText('availableInfo')).toBeInTheDocument()
    })

    it('shows no released info for a draft data source', () => {
      renderConnectorTab(undefined, false, false)
      expect(screen.queryByText('availableInfo')).not.toBeInTheDocument()
    })
  })
})
