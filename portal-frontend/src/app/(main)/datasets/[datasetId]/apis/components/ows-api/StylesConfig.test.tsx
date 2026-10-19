import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { FormProvider, useForm } from 'react-hook-form'

import messages from '@/messages/en.json'
import { API_TYPE_QUERY, OwsApiFormData, OwsApiFormSchema } from '@/types/namedApis'
import { StyleFormData } from '@/types/styles'

import { StylesConfig } from './StylesConfig'

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const DOCTYPE_ERROR_TEXT = 'Style content must not include a DOCTYPE declaration. Remove the <!DOCTYPE ...>'
const MALFORMED_ERROR_TEXT = 'Style content is not valid XML'

const makeStyle = (overrides: Partial<StyleFormData> = {}): StyleFormData => ({
  id: 'style-1',
  name: 'my-style',
  sldContent: '<?xml version="1.0"?><StyledLayerDescriptor></StyledLayerDescriptor>',
  ...overrides,
})

const Wrapper = ({ styles, isReadOnly = false }: { styles: StyleFormData[]; isReadOnly?: boolean }) => {
  const form = useForm<OwsApiFormData>({
    resolver: zodResolver(OwsApiFormSchema({ existingSlugs: [] })),
    mode: 'onChange',
    defaultValues: {
      type: API_TYPE_QUERY.OWS,
      baseInfo: { name: 'Test API', slug: 'test-api', description: '', persistence: 'postgis' },
      layers: [],
      styles,
    },
  })
  return (
    <FormProvider {...form}>
      <StylesConfig
        form={form}
        existingStyles={styles}
        selectedStyleIndex={0}
        isReadOnly={isReadOnly}
        onSelectStyle={vi.fn()}
        onAddStyle={vi.fn()}
      />
    </FormProvider>
  )
}

const renderWithIntl = (styles: StyleFormData[], isReadOnly = false) =>
  render(
    <NextIntlClientProvider locale="en" messages={messages}>
      <Wrapper styles={styles} isReadOnly={isReadOnly} />
    </NextIntlClientProvider>,
  )

describe('StylesConfig — SLD content validation feedback', () => {
  it('shows the translated DOCTYPE error under the style editor when a DOCTYPE is entered', async () => {
    renderWithIntl([makeStyle()])

    fireEvent.change(screen.getByTestId('styles.0.sldContentTextArea'), {
      target: { value: '<?xml version="1.0"?><!DOCTYPE StyledLayerDescriptor><foo/>' },
    })

    expect(await screen.findByText(DOCTYPE_ERROR_TEXT)).toBeInTheDocument()
  })

  it('shows the translated malformed-XML error for XML that is not well-formed', async () => {
    renderWithIntl([makeStyle()])

    fireEvent.change(screen.getByTestId('styles.0.sldContentTextArea'), {
      target: { value: 'not xml' },
    })

    expect(await screen.findByText(MALFORMED_ERROR_TEXT)).toBeInTheDocument()
  })

  it('shows no error for a DOCTYPE mentioned only inside a comment', async () => {
    renderWithIntl([makeStyle()])

    const textarea = screen.getByTestId('styles.0.sldContentTextArea')
    fireEvent.change(textarea, {
      target: {
        value:
          '<?xml version="1.0"?><!-- <!DOCTYPE StyledLayerDescriptor> --><StyledLayerDescriptor></StyledLayerDescriptor>',
      },
    })

    // Give the async onChange validation a render cycle, then confirm neither error rendered.
    await waitFor(() => {
      expect(screen.queryByText(DOCTYPE_ERROR_TEXT)).not.toBeInTheDocument()
      expect(screen.queryByText(MALFORMED_ERROR_TEXT)).not.toBeInTheDocument()
    })
  })

  it('clears the DOCTYPE error once the offending declaration is removed', async () => {
    renderWithIntl([makeStyle()])
    const textarea = screen.getByTestId('styles.0.sldContentTextArea')

    fireEvent.change(textarea, { target: { value: '<?xml version="1.0"?><!DOCTYPE StyledLayerDescriptor><foo/>' } })
    expect(await screen.findByText(DOCTYPE_ERROR_TEXT)).toBeInTheDocument()

    fireEvent.change(textarea, {
      target: { value: '<?xml version="1.0"?><StyledLayerDescriptor></StyledLayerDescriptor>' },
    })

    await waitFor(() => {
      expect(screen.queryByText(DOCTYPE_ERROR_TEXT)).not.toBeInTheDocument()
    })
  })
})

describe('StylesConfig — read-only mode', () => {
  it('disables the SLD content textarea when isReadOnly is true', () => {
    renderWithIntl([makeStyle()], true)

    const textarea = screen.getByTestId('styles.0.sldContentTextArea')
    expect(textarea).toBeDisabled()
  })

  it('enables the SLD content textarea when isReadOnly is false', () => {
    renderWithIntl([makeStyle()], false)

    const textarea = screen.getByTestId('styles.0.sldContentTextArea')
    expect(textarea).not.toBeDisabled()
  })
})
