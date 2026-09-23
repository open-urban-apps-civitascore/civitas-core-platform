import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'

import { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { STATUS_TYPES } from '@/types/common'
import { DataSink, DATASINK_TYPES, PROVISIONING_STATUSES } from '@/types/datasinks'
import { DATASTRUCTURE_VERSION_SOURCE, DatastructureVersion } from '@/types/datastructures'
import { LayerFormData } from '@/types/layers'
import { API_TYPE_QUERY, OwsApiFormData, OwsApiFormSchema } from '@/types/namedApis'
import { Style } from '@/types/styles'

import { LayerConfig } from './LayerConfig'

const mockStyleList: Style[] = [
  {
    id: '00000000-0000-0000-0000-000000000020',
    datasetId: '00000000-0000-0000-0000-000000000002',
    name: 'Style 1',
    sldContent: '<?xml version="1.0"?><StyledLayerDescriptor></StyledLayerDescriptor>',
    inUse: true,
    createdAt: '2026-01-01T00:00:00',
    modifiedAt: '2026-01-01T00:00:00',
  },
]

const mockDataSink: DataSink = {
  id: '00000000-0000-0000-0000-000000000003',
  datasetId: '00000000-0000-0000-0000-000000000002',
  pipelineId: '00000000-0000-0000-0000-000000000004',
  dataSinkType: DATASINK_TYPES.POSTGIS,
  provisioningStatus: PROVISIONING_STATUSES.NOT_PROVISIONED,
  configuration: {
    tableName: 'table_1',
    dataStructureVersion: {
      id: '00000000-0000-0000-0000-000000000010',
      version: '1.0.0',
      description: null,
      dataStructureVersionStatus: STATUS_TYPES.AVAILABLE,
      dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
      dataStructureId: '00000000-0000-0000-0000-000000000011',
      createdAt: '2026-01-01T00:00:00',
      modifiedAt: '2026-01-01T00:00:00',
    },
  },
  createdAt: '2026-01-01T00:00:00',
  modifiedAt: '2026-01-01T00:00:00',
}

const mockDatastructureVersion: DatastructureVersion = {
  id: '00000000-0000-0000-0000-000000000010',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: STATUS_TYPES.AVAILABLE,
  dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
  modelName: null,
  model: null,
  styles: null,
  inUse: true,
  dataStructure: { id: '00000000-0000-0000-0000-000000000011', name: 'Structure 1' },
  createdAt: '2026-01-01T00:00:00',
  modifiedAt: '2026-01-01T00:00:00',
}

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

let layerIdCounter = 0

const makeLayer = (overrides: Partial<LayerFormData> = {}): LayerFormData => ({
  id: `00000000-0000-0000-0000-${String(++layerIdCounter).padStart(12, '0')}`,
  title: 'Test Layer',
  layerName: 'test_layer',
  description: '',
  keywords: [],
  dataSinkId: '00000000-0000-0000-0000-000000000003',
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

interface WrapperProps {
  layers?: LayerFormData[]
  datastructures?: DatastructureVersion[]
  selectedLayerIndex?: number | null
  isReadOnly?: boolean
  isDeleteLayerLoading?: boolean
  onSelectLayer?: (index: number) => void
  onAddLayer?: () => void
  onDeleteLayer?: () => Promise<void>
  onTableChange?: (dataSinkId: string) => void
}

const Wrapper = ({
  layers = [],
  datastructures = [mockDatastructureVersion],
  selectedLayerIndex = null,
  isReadOnly = false,
  isDeleteLayerLoading,
  onSelectLayer = vi.fn(),
  onAddLayer = vi.fn(),
  onDeleteLayer,
  onTableChange = vi.fn(),
}: WrapperProps) => {
  const form = useForm<OwsApiFormData>({
    defaultValues: {
      type: API_TYPE_QUERY.OWS,
      baseInfo: { name: 'Test API', slug: 'test-api', description: '', persistence: 'postgis' },
      layers,
    },
  })
  return (
    <FormProvider {...form}>
      <LayerConfig
        form={form}
        styles={mockStyleList}
        postgisDataSinks={[mockDataSink]}
        postGisDatastructures={datastructures}
        selectedLayerIndex={selectedLayerIndex}
        isReadOnly={isReadOnly}
        isDeleteLayerLoading={isDeleteLayerLoading}
        onSelectLayer={onSelectLayer}
        onAddLayer={onAddLayer}
        onDeleteLayer={onDeleteLayer}
        onTableChange={onTableChange}
      />
    </FormProvider>
  )
}

const WrapperWithSchema = ({ layers, initialIndex }: { layers: LayerFormData[]; initialIndex: number }) => {
  const [selectedLayerIndex, setSelectedLayerIndex] = useState<number | null>(initialIndex)
  const form = useForm<OwsApiFormData>({
    resolver: zodResolver(OwsApiFormSchema({ existingSlugs: [] })),
    mode: 'onChange',
    defaultValues: {
      type: API_TYPE_QUERY.OWS,
      baseInfo: { name: 'Test API', slug: 'test-api', description: '', persistence: 'postgis' },
      layers,
      styles: [],
    },
  })
  return (
    <FormProvider {...form}>
      <LayerConfig
        form={form}
        styles={mockStyleList}
        postgisDataSinks={[mockDataSink]}
        postGisDatastructures={[mockDatastructureVersion]}
        selectedLayerIndex={selectedLayerIndex}
        isReadOnly={false}
        onSelectLayer={setSelectedLayerIndex}
        onAddLayer={vi.fn()}
        onTableChange={vi.fn()}
      />
    </FormProvider>
  )
}

describe('LayerConfig', () => {
  beforeEach(() => {
    layerIdCounter = 0
    vi.clearAllMocks()
  })

  describe('Duplicate layer name', () => {
    const twoLayers = () => [
      makeLayer({ title: 'Layer A', layerName: 'layer_a' }),
      makeLayer({ title: 'Layer B', layerName: 'layer_b' }),
    ]

    it('shows an error on the edited layer when the name is already used by another layer', async () => {
      render(<WrapperWithSchema layers={twoLayers()} initialIndex={1} />)

      fireEvent.change(screen.getByTestId('layers.1.layerNameTextField'), { target: { value: 'layer_a' } })

      expect(await screen.findByTestId('layers.1.layerNameFormMessage')).toHaveTextContent('common.errors.nameExists')
    })

    it('shows no error on the first layer holding the name', async () => {
      render(<WrapperWithSchema layers={twoLayers()} initialIndex={1} />)
      fireEvent.change(screen.getByTestId('layers.1.layerNameTextField'), { target: { value: 'layer_a' } })
      await screen.findByTestId('layers.1.layerNameFormMessage')

      fireEvent.click(screen.getByRole('button', { name: 'Layer A' }))

      expect(screen.queryByTestId('layers.0.layerNameFormMessage')).not.toBeInTheDocument()
    })

    it('shows no error on a third layer with a unique name', async () => {
      const layers = [...twoLayers(), makeLayer({ title: 'Layer C', layerName: 'layer_c' })]
      render(<WrapperWithSchema layers={layers} initialIndex={1} />)
      fireEvent.change(screen.getByTestId('layers.1.layerNameTextField'), { target: { value: 'layer_a' } })
      await screen.findByTestId('layers.1.layerNameFormMessage')

      fireEvent.click(screen.getByRole('button', { name: 'Layer C' }))

      expect(screen.queryByTestId('layers.2.layerNameFormMessage')).not.toBeInTheDocument()
    })

    it('clears the error once the duplicate name is changed again', async () => {
      render(<WrapperWithSchema layers={twoLayers()} initialIndex={1} />)
      const layerNameField = screen.getByTestId('layers.1.layerNameTextField')
      fireEvent.change(layerNameField, { target: { value: 'layer_a' } })
      await screen.findByTestId('layers.1.layerNameFormMessage')

      fireEvent.change(layerNameField, { target: { value: 'layer_b' } })

      await waitFor(() => {
        expect(screen.queryByTestId('layers.1.layerNameFormMessage')).not.toBeInTheDocument()
      })
    })
  })

  describe('Rendering', () => {
    it('renders the section title when layers exist', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} />)
      expect(screen.getByText('sectionTitle')).toBeInTheDocument()
    })

    it('does not render the section title when no layers exist', () => {
      render(<Wrapper layers={[]} />)
      expect(screen.queryByText('sectionTitle')).not.toBeInTheDocument()
    })

    it('renders the no-data card when no layers exist', () => {
      render(<Wrapper layers={[]} />)
      expect(screen.getByText('noData.title')).toBeInTheDocument()
      expect(screen.getByText('noData.description')).toBeInTheDocument()
    })

    it('does not render layer fields when no layer is selected', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={null} />)
      expect(screen.queryByTestId('layers.0.titleTextField')).not.toBeInTheDocument()
    })

    it('renders title and layerName fields when a layer is selected', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} />)
      expect(screen.getByTestId('layers.0.titleTextField')).toBeInTheDocument()
      expect(screen.getByTestId('layers.0.layerNameTextField')).toBeInTheDocument()
    })

    it('shows delete button when not read-only and onDeleteLayer is provided', () => {
      render(
        <Wrapper layers={[makeLayer()]} selectedLayerIndex={0} onDeleteLayer={vi.fn().mockResolvedValue(undefined)} />,
      )
      expect(screen.getByRole('button', { name: /deleteLayer/i })).toBeInTheDocument()
    })

    it('hides delete button in read-only mode', () => {
      render(
        <Wrapper
          layers={[makeLayer()]}
          selectedLayerIndex={0}
          isReadOnly
          onDeleteLayer={vi.fn().mockResolvedValue(undefined)}
        />,
      )
      expect(screen.queryByRole('button', { name: /deleteLayer/i })).not.toBeInTheDocument()
    })

    it('hides delete button when onDeleteLayer is not provided', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} />)
      expect(screen.queryByRole('button', { name: /deleteLayer/i })).not.toBeInTheDocument()
    })

    it('hides add layer buttons in read-only mode', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} isReadOnly />)
      expect(screen.queryByRole('button', { name: /addLayer/i })).not.toBeInTheDocument()
    })

    it('hides the add layer button in the header when a new layer is selected', () => {
      const newLayer = makeLayer({ id: 'new-1', title: '', layerName: '', dataSinkId: '' })
      render(<Wrapper layers={[newLayer]} selectedLayerIndex={0} />)
      expect(screen.getAllByRole('button', { name: /addLayer/i })).toHaveLength(1)
      expect(screen.getByTestId('sidebarAddLayerButton')).toBeInTheDocument()
    })

    it('hides add layer button in read-only mode when no layers exist', () => {
      render(<Wrapper layers={[]} isReadOnly />)
      expect(screen.queryByRole('button', { name: /addLayer/i })).not.toBeInTheDocument()
    })
  })

  describe('Delete layer', () => {
    it('calls onDeleteLayer directly for a new empty layer without opening the confirmation modal', () => {
      const onDeleteLayer = vi.fn().mockResolvedValue(undefined)
      const newEmptyLayer = makeLayer({ id: 'new-1', title: '', layerName: '', dataSinkId: '' })

      render(<Wrapper layers={[newEmptyLayer]} selectedLayerIndex={0} onDeleteLayer={onDeleteLayer} />)
      fireEvent.click(screen.getByRole('button', { name: /deleteLayer/i }))

      expect(onDeleteLayer).toHaveBeenCalledOnce()
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })

    it('opens confirmation modal for a layer that has content', () => {
      const onDeleteLayer = vi.fn().mockResolvedValue(undefined)

      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} onDeleteLayer={onDeleteLayer} />)
      fireEvent.click(screen.getByRole('button', { name: /deleteLayer/i }))

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
      expect(onDeleteLayer).not.toHaveBeenCalled()
    })

    it('calls onDeleteLayer when the confirm button in the modal is clicked', async () => {
      const onDeleteLayer = vi.fn().mockResolvedValue(undefined)

      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} onDeleteLayer={onDeleteLayer} />)
      fireEvent.click(screen.getByRole('button', { name: /deleteLayer/i }))
      fireEvent.click(screen.getByTestId('confirmButton'))

      expect(onDeleteLayer).toHaveBeenCalledOnce()
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })

    it('closes the modal without calling onDeleteLayer when discard is clicked', async () => {
      const onDeleteLayer = vi.fn().mockResolvedValue(undefined)

      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} onDeleteLayer={onDeleteLayer} />)
      fireEvent.click(screen.getByRole('button', { name: /deleteLayer/i }))
      fireEvent.click(screen.getByTestId('discardButton'))

      expect(onDeleteLayer).not.toHaveBeenCalled()
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })
  })

  describe('Table selection', () => {
    it('calls onTableChange with the data sink id when the table selection changes', () => {
      const onTableChange = vi.fn()
      render(<Wrapper layers={[makeLayer({ dataSinkId: '' })]} selectedLayerIndex={0} onTableChange={onTableChange} />)
      fireEvent.click(screen.getByTestId('layers.0.dataSinkIdSelectTrigger'))
      fireEvent.click(screen.getByTestId('layers.0.dataSinkIdSelectItem0'))
      expect(onTableChange).toHaveBeenCalledWith(mockDataSink.id)
    })
  })

  describe('Attribute and geometry options', () => {
    const classNode = (id: string, name: string, attributes: string[], isRoot?: boolean) => ({
      id: `node-${id}`,
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: {
          id,
          name,
          type: 'class',
          isRoot,
          attributes: attributes.map(attribute => ({ id: `${id}-${attribute}`, name: attribute, type: 'String' })),
          operations: [],
        },
        label: name,
      },
    })

    const enumNode = {
      id: 'node-e1',
      type: 'enumeration',
      position: { x: 0, y: 0 },
      data: {
        element: { id: 'e1', name: 'Colour', type: 'enumeration', literals: [{ id: 'e1-l', name: 'RED' }] },
        label: 'Colour',
      },
    }

    const versionWithNodes = (nodes: unknown[]): DatastructureVersion => ({
      ...mockDatastructureVersion,
      modelName: 'Struct',
      styles: { id: 'diagram-1', name: 'Struct', nodes, edges: [] } as unknown as UMLDiagram,
    })

    it('offers the attributes of the root class when an enumeration comes first in the diagram', () => {
      const version = versionWithNodes([enumNode, classNode('c1', 'Building', ['name', 'geom'])])

      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} datastructures={[version]} />)

      fireEvent.click(screen.getByTestId('layers.0.geometryColumnRefSelectTrigger'))
      expect(screen.getByTestId('layers.0.geometryColumnRefSelectItem0')).toHaveTextContent('name')
      expect(screen.getByTestId('layers.0.geometryColumnRefSelectItem1')).toHaveTextContent('geom')
    })

    it('offers the attributes of the designated root when several classes exist', () => {
      const version = versionWithNodes([
        classNode('c1', 'Address', ['street']),
        classNode('c2', 'Building', ['name'], true),
      ])

      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} datastructures={[version]} />)

      fireEvent.click(screen.getByTestId('layers.0.geometryColumnRefSelectTrigger'))
      expect(screen.getByTestId('layers.0.geometryColumnRefSelectItem0')).toHaveTextContent('name')
      expect(screen.queryByTestId('layers.0.geometryColumnRefSelectItem1')).not.toBeInTheDocument()
    })

    it('renders the layer fields without options for a diagram with no nodes', () => {
      render(<Wrapper layers={[makeLayer()]} selectedLayerIndex={0} datastructures={[versionWithNodes([])]} />)

      fireEvent.click(screen.getByTestId('layers.0.geometryColumnRefSelectTrigger'))
      expect(screen.queryByTestId('layers.0.geometryColumnRefSelectItem0')).not.toBeInTheDocument()
      expect(screen.getByText('dataSelection.attributes')).toBeInTheDocument()
    })
  })

  describe('Calculate bounding box from CRS', () => {
    it('fills bounding box fields with the CRS native bounds on click', () => {
      render(<Wrapper layers={[makeLayer({ crs: 'EPSG:4326' })]} selectedLayerIndex={0} />)

      fireEvent.click(screen.getByRole('button', { name: /geometry.calculateFromCrs/i }))

      expect(screen.getByTestId('layers.0.nativeBoundingBox.minXTextField')).toHaveValue('-180')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.minYTextField')).toHaveValue('-90')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.maxXTextField')).toHaveValue('180')
      expect(screen.getByTestId('layers.0.nativeBoundingBox.maxYTextField')).toHaveValue('90')
    })

    it('is disabled when no CRS is selected', () => {
      render(<Wrapper layers={[makeLayer({ crs: '' })]} selectedLayerIndex={0} />)
      expect(screen.getByRole('button', { name: /geometry.calculateFromCrs/i })).toBeDisabled()
    })

    it('is disabled in read-only mode', () => {
      render(<Wrapper layers={[makeLayer({ crs: 'EPSG:4326' })]} selectedLayerIndex={0} isReadOnly />)
      expect(screen.getByRole('button', { name: /geometry.calculateFromCrs/i })).toBeDisabled()
    })
  })
})
