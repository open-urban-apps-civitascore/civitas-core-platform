import { zodResolver } from '@hookform/resolvers/zod'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import { emptyHttpAuthentication, HttpAuthenticationType } from '@/types/connectors'
import type { DatasourceFormDraft } from '@/types/datasources'
import { DatasourceFormDraftSchema } from '@/types/datasources'

import { ConnectorTab } from './ConnectorTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))
vi.mock('./connectorSources', async importOriginal => ({
  CONNECTOR_INPUTS: {
    HTTP: (await importOriginal<typeof import('./connectorSources')>()).CONNECTOR_INPUTS.HTTP,
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
  isConnectorComplete = false,
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
          datasourceId={merged.id}
          connectorType={merged.connectorType}
          isConnectorComplete={isConnectorComplete}
          isConnectorChanged={false}
          isReadOnly={isReadOnly}
          isDatasourceReleased={isDatasourceReleased}
        />
      </Form>
    )
  }

  return render(
    <QueryClientProvider client={new QueryClient()}>
      <Wrapper />
    </QueryClientProvider>,
  )
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
    expect(screen.getByText('types.MQTT')).toBeInTheDocument()
    fireEvent.click(select)
    await screen.findByText('types.SQL')
    expect(screen.getByText('types.SQL')).toBeInTheDocument()
    expect(screen.getByText('types.HTTP')).toBeInTheDocument()
  })

  it('does not render config when no connector type selected', async () => {
    renderConnectorTab({ connectorType: undefined, configuration: undefined })

    expect(screen.getByText('title1')).toBeInTheDocument()
    expect(screen.getByLabelText('type')).toBeInTheDocument()
    expect(screen.queryByText('types.MQTT')).not.toBeInTheDocument()
    expect(screen.queryByText('types.SQL')).not.toBeInTheDocument()
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

  describe('HTTP connector', () => {
    const httpConfiguration = (type: 'NONE' | 'BASIC') => ({
      connectorType: 'HTTP' as const,
      configuration: { baseUrl: '', authentication: { type, username: '', password: '' } },
    })

    it('shows the API host and the authentication type, but no credentials for None', () => {
      renderConnectorTab(httpConfiguration('NONE'))

      expect(screen.getByLabelText(/http\.baseUrl\.label/)).toBeInTheDocument()
      expect(screen.getByLabelText(/http\.authentication\.label/)).toBeInTheDocument()
      expect(screen.getByText('http.authentication.none')).toBeInTheDocument()
      expect(screen.queryByLabelText(/http\.username\.label/)).not.toBeInTheDocument()
      expect(screen.queryByLabelText(/http\.password\.label/)).not.toBeInTheDocument()
    })

    it('shows username and password for Basic', () => {
      renderConnectorTab(httpConfiguration('BASIC'))

      expect(screen.getByLabelText(/http\.username\.label/)).toBeInTheDocument()
      expect(screen.getByLabelText(/http\.password\.label/)).toHaveAttribute('type', 'password')
    })

    it('removes spaces and trailing slashes from the API host when the field loses focus', async () => {
      renderConnectorTab(httpConfiguration('NONE'))
      const baseUrl = screen.getByLabelText(/http\.baseUrl\.label/)

      fireEvent.change(baseUrl, { target: { value: '  https://api.example.org/v1/  ' } })
      fireEvent.blur(baseUrl)

      await waitFor(() => expect(baseUrl).toHaveValue('https://api.example.org/v1'))
    })

    it('turns off browser autocomplete for the credentials', () => {
      renderConnectorTab(httpConfiguration('BASIC'))

      expect(screen.getByLabelText(/http\.username\.label/)).toHaveAttribute('autocomplete', 'off')
      expect(screen.getByLabelText(/http\.password\.label/)).toHaveAttribute('autocomplete', 'new-password')
    })

    it('shows the credentials after the user selects Basic', async () => {
      renderConnectorTab(httpConfiguration('NONE'))

      fireEvent.click(screen.getByLabelText(/http\.authentication\.label/))
      fireEvent.click(await screen.findByText('http.authentication.basic'))

      expect(await screen.findByLabelText(/http\.username\.label/)).toBeInTheDocument()
      expect(screen.getByLabelText(/http\.password\.label/)).toBeInTheDocument()
    })

    describe('authentication types', () => {
      const FIELD_LABELS: Record<HttpAuthenticationType, RegExp[]> = {
        NONE: [],
        BASIC: [/http\.username\.label/, /http\.password\.label/],
        BEARER_TOKEN: [/http\.token\.label/],
        API_TOKEN: [/http\.apiTokenName\.label/, /http\.apiTokenValue\.label/, /http\.apiTokenIn\.label/],
        OAUTH2: [/http\.tokenUrl\.label/, /http\.clientId\.label/, /http\.clientSecret\.label/, /http\.scope\.label/],
        CLIENT_CERTIFICATE: [/http\.certificate\.label/, /http\.privateKey\.label/],
      }
      const SECRET_LABELS = [
        ['BASIC', /http\.password\.label/],
        ['BEARER_TOKEN', /http\.token\.label/],
        ['API_TOKEN', /http\.apiTokenValue\.label/],
        ['OAUTH2', /http\.clientSecret\.label/],
      ] as const
      const ALL_LABELS = Object.values(FIELD_LABELS).flat()

      const renderWithAuthentication = (type: HttpAuthenticationType, baseUrl = '') =>
        renderConnectorTab({
          connectorType: 'HTTP',
          configuration: { baseUrl, authentication: emptyHttpAuthentication(type) },
        })

      it('offers None, Basic, Bearer token, API token, OAuth2 and client certificate', async () => {
        renderWithAuthentication('NONE')

        fireEvent.click(screen.getByLabelText(/http\.authentication\.label/))

        const options = await screen.findAllByRole('option')
        expect(options.map(option => option.textContent)).toEqual([
          'http.authentication.none',
          'http.authentication.basic',
          'http.authentication.bearerToken',
          'http.authentication.apiToken',
          'http.authentication.oauth2',
          'http.authentication.clientCertificate',
        ])
      })

      it.each(['BEARER_TOKEN', 'API_TOKEN', 'OAUTH2', 'CLIENT_CERTIFICATE'] as const)(
        'shows only the fields of %s',
        type => {
          renderWithAuthentication(type)

          const ownLabels = FIELD_LABELS[type]
          for (const label of ownLabels) {
            expect(screen.getByLabelText(label)).toBeInTheDocument()
          }
          for (const label of ALL_LABELS.filter(other => !ownLabels.includes(other))) {
            expect(screen.queryByLabelText(label)).not.toBeInTheDocument()
          }
        },
      )

      it.each(SECRET_LABELS)('masks the secret of %s and turns off autocomplete', (type, label) => {
        renderWithAuthentication(type)

        const secret = screen.getByLabelText(label)
        expect(secret).toHaveAttribute('type', 'password')
        expect(secret).toHaveAttribute('autocomplete', 'new-password')
      })

      it.each([
        ['API_TOKEN', /http\.apiTokenName\.label/],
        ['OAUTH2', /http\.clientId\.label/],
      ] as const)('turns off autocomplete for the visible fields of %s', (type, label) => {
        renderWithAuthentication(type)

        expect(screen.getByLabelText(label)).toHaveAttribute('autocomplete', 'off')
      })

      it.each([
        ['certificate', /http\.certificate\.label/],
        ['privateKey', /http\.privateKey\.label/],
      ])('selects the %s as a PEM file', (field, label) => {
        renderWithAuthentication('CLIENT_CERTIFICATE')

        expect(screen.getByLabelText(label)).toHaveValue('noFile')
        expect(screen.getByTestId(`configuration.authentication.${field}FileInput`)).toHaveAttribute(
          'accept',
          '.pem,.crt,.cer,.key',
        )
      })

      it.each([
        ['certificate', 'datasources.errors.invalidCertificate'],
        ['privateKey', 'datasources.errors.invalidPrivateKey'],
      ])('shows the PEM error right after the user selects an invalid %s file', async (field, message) => {
        renderWithAuthentication('CLIENT_CERTIFICATE')

        await userEvent.upload(
          screen.getByTestId(`configuration.authentication.${field}FileInput`),
          new File(['not a PEM file'], 'invalid.pem'),
        )

        expect(await screen.findByText(message)).toBeInTheDocument()
      })
    })

    it('disables the HTTP fields in read-only mode', () => {
      renderConnectorTab(httpConfiguration('BASIC'), true)

      expect(screen.getByLabelText(/http\.baseUrl\.label/)).toBeDisabled()
      expect(screen.getByLabelText(/http\.authentication\.label/)).toBeDisabled()
      expect(screen.getByLabelText(/http\.username\.label/)).toBeDisabled()
      expect(screen.getByLabelText(/http\.password\.label/)).toBeDisabled()
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

    it('enables the connection test for a released data source in read-only mode', () => {
      renderConnectorTab(undefined, true, true, true)
      expect(screen.getByRole('button', { name: 'button' })).toBeEnabled()
    })
  })
})
