import {
  BoundingBox,
  DEFAULTS_BY_TYPE,
  Layer,
  LayerApiPayload,
  LayerFormData,
  StaApiFormData,
  StaApiPayloadData,
  WfsWmsApiApiPayloadData,
  WfsWmsApiFormData,
} from '@/types/namedApis'

export const toBoundingBoxPayload = (bbox: WfsWmsApiFormData['layer']['nativeBoundingBox']) => ({
  ...bbox,
  minX: Number(bbox.minX),
  minY: Number(bbox.minY),
  maxX: Number(bbox.maxX),
  maxY: Number(bbox.maxY),
})

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
    crs: layer.crs,
    bboxAutoCalculate: false,
    nativeBoundingBox: toBoundingBoxFormData(layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxFormData(layer.latLonBoundingBox),
    defaultStyleId: layer.defaultStyleId || '',
    alternativeStyleIds: layer.alternativeStyleIds,
  }))

export const mapFormLayerToPayload = (layers: LayerFormData[]): LayerApiPayload[] =>
  layers.map(layer => ({
    ...layer,
    defaultStyleId: layer.defaultStyleId || null,
    nativeBoundingBox: toBoundingBoxPayload(layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxPayload(layer.latLonBoundingBox),
  }))

export const buildStaPayloadData = (data: StaApiFormData): StaApiPayloadData => ({
  baseInfo: { ...data.baseInfo, standard: DEFAULTS_BY_TYPE.sensorthings.standard },
})

export const buildWfsWmsPayload = (data: WfsWmsApiFormData): WfsWmsApiApiPayloadData => ({
  baseInfo: {
    ...data.baseInfo,
    standard: DEFAULTS_BY_TYPE['wfs-wms'].standard,
    description: data.baseInfo.description || undefined,
  },
  layer: {
    ...data.layer,
    dataSinkId: '',
    description: data.layer.description || undefined,
    nativeBoundingBox: toBoundingBoxPayload(data.layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxPayload(data.layer.latLonBoundingBox),
    defaultStyleId: data.layer.defaultStyleId || null,
    alternativeStyleIds: data.layer.alternativeStyleIds,
  },
})
