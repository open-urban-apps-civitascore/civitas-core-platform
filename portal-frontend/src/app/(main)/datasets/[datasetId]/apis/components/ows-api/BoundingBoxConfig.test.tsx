import { render, screen, waitFor, within } from '@testing-library/react'
import { useEffect } from 'react'
import { FormProvider, useForm } from 'react-hook-form'

import { API_TYPE_QUERY, LayerFormData, OwsApiFormData } from '@/types/namedApis'

import { BoundingBoxConfig } from './BoundingBoxConfig'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const makeLayer = (overrides: Partial<LayerFormData> = {}): LayerFormData => ({
  id: '00000000-0000-0000-0000-000000000001',
  title: 'Test Layer',
  layerName: 'test_layer',
  description: '',
  keywords: [],
  dataSinkId: '00000000-0000-0000-0000-000000000002',
  attribute: ['attr1'],
  cqlFilter: '',
  geometryColumnRef: 'geom',
  nativeCRS: 'EPSG:25832',
  crs: 'EPSG:4326',
  bboxAutoCalculate: false,
  nativeBoundingBox: { minX: '5.8', minY: '47.2', maxX: '15.0', maxY: '55.0', crs: 'EPSG:25832' },
  latLonBoundingBox: { minX: '5.8', minY: '47.2', maxX: '15.0', maxY: '55.0', crs: 'EPSG:25832' },
  defaultStyleId: null,
  alternativeStyleIds: [],
  ...overrides,
})

const BBOX_FIELD_TESTIDS = [
  'layers.0.nativeBoundingBox.minXTextField',
  'layers.0.nativeBoundingBox.minYTextField',
  'layers.0.nativeBoundingBox.maxXTextField',
  'layers.0.nativeBoundingBox.maxYTextField',
] as const

const BBOX_ERROR_FIELD_TESTIDS = [
  'layers.0.nativeBoundingBox.minXFormMessage',
  'layers.0.nativeBoundingBox.minYFormMessage',
  'layers.0.nativeBoundingBox.maxXFormMessage',
  'layers.0.nativeBoundingBox.maxYFormMessage',
] as const

interface WrapperProps {
  layer?: LayerFormData
  isDisabled?: boolean
}

const Wrapper = ({ layer = makeLayer(), isDisabled }: WrapperProps) => {
  const form = useForm<OwsApiFormData>({
    defaultValues: {
      type: API_TYPE_QUERY.OWS,
      baseInfo: { name: 'Test API', slug: 'test-api', description: '', persistence: 'postgis' },
      layers: [layer],
    },
  })
  return (
    <FormProvider {...form}>
      <BoundingBoxConfig form={form} layerFieldIndex={0} isDisabled={isDisabled} />
    </FormProvider>
  )
}

type BboxField = 'minX' | 'minY' | 'maxX' | 'maxY'

const WrapperWithErrors = ({ fields }: { fields: BboxField[] }) => {
  const form = useForm<OwsApiFormData>({
    defaultValues: {
      type: API_TYPE_QUERY.OWS,
      baseInfo: { name: 'Test API', slug: 'test-api', description: '', persistence: 'postgis' },
      layers: [makeLayer()],
    },
  })
  useEffect(() => {
    for (const field of fields) {
      form.setError(`layers.0.nativeBoundingBox.${field}`, { message: 'common.errors.required' })
    }
  }, [fields, form])
  return (
    <FormProvider {...form}>
      <BoundingBoxConfig form={form} layerFieldIndex={0} />
    </FormProvider>
  )
}

describe('BoundingBoxConfig', () => {
  describe('Rendering', () => {
    it('renders the bounding box label', () => {
      render(<Wrapper />)
      expect(screen.getByText('geometry.boundingBox')).toBeInTheDocument()
    })

    it('marks the field as required', () => {
      render(<Wrapper />)
      const outerLabel = screen.getByText('geometry.boundingBox').closest('label')!
      expect(within(outerLabel).getByText('*')).toBeInTheDocument()
    })

    it('renders all four coordinate input fields', () => {
      render(<Wrapper />)
      for (const testId of BBOX_FIELD_TESTIDS) {
        expect(screen.getByTestId(testId)).toBeInTheDocument()
      }
    })

    it('renders the field labels minX, minY, maxX, maxY', () => {
      render(<Wrapper />)
      expect(screen.getByText('minX')).toBeInTheDocument()
      expect(screen.getByText('minY')).toBeInTheDocument()
      expect(screen.getByText('maxX')).toBeInTheDocument()
      expect(screen.getByText('maxY')).toBeInTheDocument()
    })

    it('renders the pre-filled values from the form', () => {
      render(<Wrapper />)
      expect(screen.getByTestId('layers.0.nativeBoundingBox.minXTextField')).toHaveValue('5.8')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.minYTextField')).toHaveValue('47.2')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.maxXTextField')).toHaveValue('15.0')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.maxYTextField')).toHaveValue('55.0')
    })

    it('does not show the combined error message when there are no errors', () => {
      render(<Wrapper />)
      expect(screen.queryByTestId('nativeBoundingBoxFormMessage')).not.toBeInTheDocument()
    })
  })

  describe('Disabled state', () => {
    it('enables all fields by default', () => {
      render(<Wrapper />)
      for (const testId of BBOX_FIELD_TESTIDS) {
        expect(screen.getByTestId(testId)).not.toBeDisabled()
      }
    })

    it('disables all fields when isDisabled is true', () => {
      render(<Wrapper isDisabled />)
      for (const testId of BBOX_FIELD_TESTIDS) {
        expect(screen.getByTestId(testId)).toBeDisabled()
      }
    })
  })

  describe('Error handling', () => {
    it('shows the combined error message when a bbox field has a validation error', async () => {
      render(<WrapperWithErrors fields={['minX']} />)
      await waitFor(() => {
        expect(screen.getByTestId('nativeBoundingBoxFormMessage')).toBeInTheDocument()
        expect(screen.getByTestId('nativeBoundingBoxFormMessage')).toHaveTextContent('common.errors.required')
      })
    })

    it('shows only one combined error message even when multiple fields have errors', async () => {
      render(<WrapperWithErrors fields={['minX', 'maxX']} />)
      await waitFor(() => {
        expect(screen.getAllByTestId('nativeBoundingBoxFormMessage')).toHaveLength(1)
      })
    })

    it('does not show individual field error messages even when a field has an error', async () => {
      render(<WrapperWithErrors fields={['minX']} />)
      await waitFor(() => {
        expect(screen.getByTestId('nativeBoundingBoxFormMessage')).toBeInTheDocument()
      })
      for (const testId of BBOX_ERROR_FIELD_TESTIDS) {
        expect(screen.queryByTestId(testId)).not.toBeInTheDocument()
      }
    })
  })
})
