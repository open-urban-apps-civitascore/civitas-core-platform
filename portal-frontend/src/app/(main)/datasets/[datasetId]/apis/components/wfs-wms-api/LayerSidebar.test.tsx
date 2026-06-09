import { fireEvent, render, screen } from '@testing-library/react'

import { LayerFormData } from '@/types/namedApis'

import { LayerSidebar } from './LayerSidebar'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, options?: Record<string, unknown>) =>
    options !== undefined ? `${key}:${String(options.index)}` : key,
}))

const makeLayer = (overrides: Partial<LayerFormData> = {}): LayerFormData => ({
  id: '00000000-0000-0000-0000-000000000001',
  title: 'Test Layer',
  layerName: 'test_layer',
  description: '',
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

interface RenderProps {
  existingLayers?: LayerFormData[]
  selectedLayerIndex?: number | null
  isReadOnly?: boolean
  onSelectLayer?: (index: number) => void
  onAddLayer?: () => void
}

const renderSidebar = ({
  existingLayers = [],
  selectedLayerIndex = null,
  isReadOnly = false,
  onSelectLayer = vi.fn(),
  onAddLayer = vi.fn(),
}: RenderProps = {}) =>
  render(
    <LayerSidebar
      existingLayers={existingLayers}
      selectedLayerIndex={selectedLayerIndex}
      isReadOnly={isReadOnly}
      onSelectLayer={onSelectLayer}
      onAddLayer={onAddLayer}
    />,
  )

describe('LayerSidebar', () => {
  describe('Layer display titles', () => {
    it('renders the title of each layer', () => {
      renderSidebar({
        existingLayers: [makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'My Layer' })],
      })
      expect(screen.getByRole('button', { name: 'My Layer' })).toBeInTheDocument()
    })

    it('uses the "newLayer" fallback when the layer has no title and id starts with "new-"', () => {
      renderSidebar({
        existingLayers: [makeLayer({ id: 'new-1', title: '' })],
      })
      expect(screen.getByRole('button', { name: 'newLayer:1' })).toBeInTheDocument()
    })

    it('uses the "untitledLayer" fallback when a saved layer has no title', () => {
      renderSidebar({
        existingLayers: [makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: '' })],
      })
      expect(screen.getByRole('button', { name: 'untitledLayer:1' })).toBeInTheDocument()
    })

    it('counts untitledIndex as only untitled layers up to and including the current one', () => {
      renderSidebar({
        existingLayers: [
          makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'Named Layer' }),
          makeLayer({ id: '00000000-0000-0000-0000-000000000002', title: '' }),
          makeLayer({ id: '00000000-0000-0000-0000-000000000003', title: '' }),
        ],
      })
      expect(screen.getByRole('button', { name: 'untitledLayer:1' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'untitledLayer:2' })).toBeInTheDocument()
    })
  })

  describe('Selection state', () => {
    it('applies active styles to the selected layer button', () => {
      renderSidebar({
        existingLayers: [makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'Layer 1' })],
        selectedLayerIndex: 0,
      })
      expect(screen.getByRole('button', { name: 'Layer 1' })).toHaveClass('bg-muted', 'font-medium')
    })

    it('does not apply active styles to non-selected layer buttons', () => {
      renderSidebar({
        existingLayers: [
          makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'Layer 1' }),
          makeLayer({ id: '00000000-0000-0000-0000-000000000002', title: 'Layer 2' }),
        ],
        selectedLayerIndex: 0,
      })
      expect(screen.getByRole('button', { name: 'Layer 2' })).not.toHaveClass('bg-muted')
      expect(screen.getByRole('button', { name: 'Layer 2' })).not.toHaveClass('font-medium')
    })

    it('does not apply active styles to any button when selectedLayerIndex is null', () => {
      renderSidebar({
        existingLayers: [makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'Layer 1' })],
        selectedLayerIndex: null,
      })
      expect(screen.getByRole('button', { name: 'Layer 1' })).not.toHaveClass('bg-muted')
    })
  })

  describe('Interactions', () => {
    it('calls onSelectLayer with the correct index when a layer is clicked', () => {
      const onSelectLayer = vi.fn()
      renderSidebar({
        existingLayers: [
          makeLayer({ id: '00000000-0000-0000-0000-000000000001', title: 'Layer 1' }),
          makeLayer({ id: '00000000-0000-0000-0000-000000000002', title: 'Layer 2' }),
        ],
        onSelectLayer,
      })
      fireEvent.click(screen.getByRole('button', { name: 'Layer 2' }))
      expect(onSelectLayer).toHaveBeenCalledWith(1)
    })

    it('calls onAddLayer when the add layer button is clicked', () => {
      const onAddLayer = vi.fn()
      renderSidebar({ onAddLayer })
      fireEvent.click(screen.getByTestId('sidebarAddLayerButton'))
      expect(onAddLayer).toHaveBeenCalledOnce()
    })
  })

  describe('Read-only mode', () => {
    it('renders the "Add Layer" button when not read-only', () => {
      renderSidebar({ isReadOnly: false })
      expect(screen.getByTestId('sidebarAddLayerButton')).toBeInTheDocument()
    })

    it('does not render the "Add Layer" button when read-only', () => {
      renderSidebar({ isReadOnly: true })
      expect(screen.queryByTestId('sidebarAddLayerButton')).not.toBeInTheDocument()
    })
  })

  describe('Edge cases', () => {
    it('renders no layer buttons when existingLayers is empty', () => {
      renderSidebar({ existingLayers: [] })
      expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    })
  })
})
