import {
  ApiStandard,
  BoundingBox,
  DEFAULTS_BY_TYPE,
  Layer,
  LayerApiPayload,
  LayerFormData,
  NamedApi,
  NamedApiPayload,
  StaApiFormData,
  Style,
  StyleApiPayload,
  StyleFormData,
  WfsWmsApiFormData,
} from '@/types/namedApis'

export const hasApiType = (apis: NamedApi[], apiType: ApiStandard): boolean =>
  apis.some(api => api.standard === apiType)

type BoundingBoxPayload = { minX: number; minY: number; maxX: number; maxY: number; crs: string }

export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox']): BoundingBoxPayload
export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox'] | null): BoundingBoxPayload | null
export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox'] | null): BoundingBoxPayload | null {
  if (!bbox) return null
  return { ...bbox, minX: Number(bbox.minX), minY: Number(bbox.minY), maxX: Number(bbox.maxX), maxY: Number(bbox.maxY) }
}

export const toBoundingBoxFormData = (bbox: BoundingBox) => ({
  ...bbox,
  minX: String(bbox.minX),
  minY: String(bbox.minY),
  maxX: String(bbox.maxX),
  maxY: String(bbox.maxY),
})

export const mapApiLayerToFormData = (layers: Layer[]): LayerFormData[] =>
  layers.map(layer => ({
    id: layer.id,
    title: layer.title,
    layerName: layer.layerName,
    description: layer.description || '',
    dataSinkId: layer.dataSinkId,
    attribute: layer.attribute,
    cqlFilter: layer.cqlFilter || '',
    geometryColumnRef: layer.geometryColumnRef,
    nativeCRS: layer.nativeCRS,
    crs: layer.crs || layer.nativeCRS,
    bboxAutoCalculate: false,
    nativeBoundingBox: toBoundingBoxFormData(layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxFormData(layer.latLonBoundingBox),
    defaultStyleId: layer.defaultStyleId || '',
    alternativeStyleIds: layer.alternativeStyleIds,
  }))

export const mapFormLayerToPayload = (layers: LayerFormData[]): LayerApiPayload[] =>
  layers.map(layer => ({
    ...layer,
    defaultStyleId: layer.defaultStyleId && layer.defaultStyleId !== 'none' ? layer.defaultStyleId : null,
    nativeBoundingBox: toBoundingBoxPayload(layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxPayload(layer.latLonBoundingBox),
  }))

export const buildStaPayloadData = (data: StaApiFormData): NamedApiPayload => ({
  ...data.baseInfo,
  standard: DEFAULTS_BY_TYPE.sensorthings.standard,
})

export const buildWfsWmsPayload = (data: WfsWmsApiFormData): NamedApiPayload => ({
  ...data.baseInfo,
  standard: DEFAULTS_BY_TYPE['wfs-wms'].standard,
  description: data.baseInfo.description || undefined,
})

export const mapApiStyleToFormData = (styles: Style[]): StyleFormData[] =>
  styles.map(style => ({
    id: style.id,
    name: style.name,
    sldContent: style.sldContent,
  }))

export const mapFormStyleToPayload = (style: StyleFormData): StyleApiPayload => ({
  name: style.name.trim(),
  sldContent: style.sldContent,
})
