import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { FormProvider, useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { API_TYPE_QUERY, ApiTypeQuery, DEFAULTS_BY_TYPE, StaApiFormData, StaApiFormSchema } from '@/types/namedApis'

import { BaseInfoForm } from './BaseInfoForm'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

interface WrapperProps {
  apiType: ApiTypeQuery
  isReadOnly?: boolean
  existingSlugs?: string[]
  defaultValues?: Partial<StaApiFormData['baseInfo']>
  urlPreviewSlug?: string
  onSlugBlur?: () => void
}

const Wrapper = (props: WrapperProps) => {
  const {
    apiType,
    isReadOnly = false,
    existingSlugs = [],
    defaultValues,
    urlPreviewSlug,
    onSlugBlur = () => undefined,
  } = props
  const defaults = DEFAULTS_BY_TYPE[apiType]
  const schema = StaApiFormSchema({ existingSlugs })
  const form = useForm<StaApiFormData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: {
      type: API_TYPE_QUERY.SENSORTHINGS,
      baseInfo: {
        name: defaultValues?.name ?? '',
        slug: defaultValues?.slug ?? defaults.defaultSlug,
        description: defaultValues?.description ?? '',
        persistence: defaultValues?.persistence ?? defaults.persistenceValue,
      },
    },
  })

  return (
    <FormProvider {...form}>
      <BaseInfoForm
        form={form}
        apiType={apiType}
        isReadOnly={isReadOnly}
        datasetId="dataset-123"
        typeLabel={apiType === API_TYPE_QUERY.SENSORTHINGS ? 'SensorThings API' : 'WFS/WMS API'}
        urlPreviewSlug={urlPreviewSlug ?? defaults.defaultSlug}
        onSlugBlur={onSlugBlur}
      />
    </FormProvider>
  )
}

describe('ApiConfigForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('SensorThings variant', () => {
    test('renders read-only persistence as text "FROST Server"', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      expect(screen.getByTestId('apiPersistenceReadOnly')).toHaveTextContent('FROST Server')
      expect(screen.queryByTestId('persistenceSelectTrigger')).not.toBeInTheDocument()
    })

    test('pre-fills slug as "sta"', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      expect(screen.getByTestId('slugTextField')).toHaveValue('sta')
    })

    test('shows type label', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      expect(screen.getByTestId('apiTypeReadOnly')).toHaveTextContent('SensorThings API')
    })
  })

  describe('WFS/WMS variant', () => {
    test('renders persistence as a dropdown', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.WFS_WMS} />)
      expect(screen.getByTestId('baseInfo.persistenceSelectTrigger')).toBeInTheDocument()
      expect(screen.queryByTestId('apiPersistenceReadOnly')).not.toBeInTheDocument()
    })

    test('pre-fills slug as "wfswms"', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.WFS_WMS} />)
      expect(screen.getByTestId('slugTextField')).toHaveValue('wfswms')
    })
  })

  describe('Read-only mode', () => {
    test('disables editable fields', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} isReadOnly />)
      expect(screen.getByTestId('slugTextField')).toBeDisabled()
      expect(screen.getByTestId('baseInfo.nameTextField')).toBeDisabled()
      expect(screen.getByTestId('baseInfo.descriptionTextArea')).toBeDisabled()
    })
  })

  describe('URL preview', () => {
    test('renders the preview path with the /v1 data-plane scheme and slug bolded', () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} urlPreviewSlug="my-slug" />)
      const preview = screen.getByTestId('apiUrlPreview')
      // The gateway serves named APIs at /v1/datasets/{id}/{slug} (issue #1368).
      expect(preview).toHaveTextContent('/v1/datasets/dataset-123/my-slug')
      expect(preview.textContent?.startsWith('/v1/datasets/')).toBe(true)
      expect(preview.querySelector('strong')).toHaveTextContent('my-slug')
    })

    test('calls onSlugBlur when slug field is blurred', () => {
      const onSlugBlur = vi.fn()
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} onSlugBlur={onSlugBlur} />)
      const slug = screen.getByTestId('slugTextField')
      fireEvent.change(slug, { target: { value: 'newslug' } })
      fireEvent.blur(slug)
      expect(onSlugBlur).toHaveBeenCalled()
    })
  })

  describe('Slug validation', () => {
    const triggerSlugChange = async (value: string) => {
      const slug = screen.getByTestId('slugTextField')
      fireEvent.change(slug, { target: { value } })
      fireEvent.blur(slug)
    }

    test('shows required error when slug is empty', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange('')
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.required')
      })
    })

    test('shows invalidFormat error for uppercase letters', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange('Foo')
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.invalidFormat')
      })
    })

    test('shows invalidFormat error for leading hyphen', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange('-bar')
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.invalidFormat')
      })
    })

    test('shows invalidFormat error for trailing hyphen', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange('bar-')
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.invalidFormat')
      })
    })

    // Mirrors the backend blocklist NamedApiAllowedSlugValidator.RESERVED.
    test.each(['apis', 'api', 'v1', 'admin'])('blocks the reserved slug "%s"', async reservedSlug => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange(reservedSlug)
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.reserved')
      })
    })

    test('blocks slugs that already exist on the dataset', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} existingSlugs={['taken-slug']} />)
      await triggerSlugChange('taken-slug')
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.notUnique')
      })
    })

    test('rejects slugs longer than 32 characters', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      // Build a 33-char string of valid chars to trigger the length rule (regex would otherwise pass).
      const longSlug = 'a'.repeat(33)
      await triggerSlugChange(longSlug)
      await waitFor(() => {
        expect(screen.getByTestId('slugFormMessage')).toHaveTextContent('apis.config.errors.slug.tooLong')
      })
    })

    test('accepts valid slugs', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      await triggerSlugChange('valid-slug-1')
      await waitFor(() => {
        // FormMessage renders nothing when there is no error (returns null)
        expect(screen.queryByTestId('slugFormMessage')).not.toBeInTheDocument()
      })
    })
  })

  describe('Description validation', () => {
    test('accepts a description of exactly 150 characters', async () => {
      render(<Wrapper apiType={API_TYPE_QUERY.SENSORTHINGS} />)
      const description = screen.getByTestId('baseInfo.descriptionTextArea') as HTMLTextAreaElement
      const text = 'a'.repeat(150)
      fireEvent.change(description, { target: { value: text } })
      await waitFor(() => {
        expect(description).toHaveValue(text)
      })
    })
  })
})
