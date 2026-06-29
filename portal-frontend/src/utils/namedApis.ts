import proj4 from 'proj4'

import { UMLClass } from '@/components/uml-modeler/types/uml'
import { crsOptions } from '@/const/crs'
import { DataSink } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
import { BoundingBox, Layer, LayerApiPayload, LayerFormData } from '@/types/layers'
import {
  ApiStandard,
  DEFAULTS_BY_TYPE,
  NamedApi,
  NamedApiPayload,
  OwsApiFormData,
  StaApiFormData,
} from '@/types/namedApis'
import { Style, StyleFormData, StyleInput } from '@/types/styles'

export const hasApiType = (apis: NamedApi[], apiType: ApiStandard): boolean =>
  apis.some(api => api.standard === apiType)

type BoundingBoxPayload = { minX: number; minY: number; maxX: number; maxY: number; crs: string }

export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox']): BoundingBoxPayload
export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox'] | null): BoundingBoxPayload | null
export function toBoundingBoxPayload(bbox: LayerFormData['nativeBoundingBox'] | null): BoundingBoxPayload | null {
  if (!bbox) return null
  return { ...bbox, minX: Number(bbox.minX), minY: Number(bbox.minY), maxX: Number(bbox.maxX), maxY: Number(bbox.maxY) }
}

export const toBoundingBoxFormData = (bbox: BoundingBox | null) => ({
  ...bbox,
  minX: String(bbox?.minX ?? ''),
  minY: String(bbox?.minY ?? ''),
  maxX: String(bbox?.maxX ?? ''),
  maxY: String(bbox?.maxY ?? ''),
  crs: bbox?.crs ?? '',
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
    keywords: layer.keywords,
    defaultStyleId: layer.defaultStyleId || '',
    alternativeStyleIds: layer.alternativeStyleIds,
  }))

// The Lat/Lon Bbox refers to the CRS 'EPSG:4326' and is used in the GetCapabilities Geoserver request
// This function transferes the values from the native bbox to lat/lon coordinates
const computeLatLonBoundingBox = (
  nativeBoundingBox: LayerFormData['nativeBoundingBox'],
  crs: string,
): LayerFormData['latLonBoundingBox'] | null => {
  const crsOption = crsOptions.find(o => o.value === crs)
  if (!crsOption) return null
  const minX = Number(nativeBoundingBox.minX)
  const minY = Number(nativeBoundingBox.minY)
  const maxX = Number(nativeBoundingBox.maxX)
  const maxY = Number(nativeBoundingBox.maxY)
  if (!Number.isFinite(minX) || !Number.isFinite(minY) || !Number.isFinite(maxX) || !Number.isFinite(maxY)) return null
  const [lon1, lat1] = proj4(crsOption.proj4def, 'EPSG:4326', [minX, minY])
  const [lon2, lat2] = proj4(crsOption.proj4def, 'EPSG:4326', [maxX, maxY])
  return { minX: String(lon1), minY: String(lat1), maxX: String(lon2), maxY: String(lat2), crs: 'EPSG:4326' }
}

export const mapFormLayerToPayload = (layers: LayerFormData[]): LayerApiPayload[] =>
  layers.map(({ id: _id, nativeCRS: _nativeCRS, ...layer }) => ({
    ...layer,
    defaultStyleId: layer.defaultStyleId && layer.defaultStyleId !== 'none' ? layer.defaultStyleId : null,
    nativeBoundingBox: layer.bboxAutoCalculate ? null : toBoundingBoxPayload(layer.nativeBoundingBox),
    latLonBoundingBox: layer.bboxAutoCalculate
      ? null
      : toBoundingBoxPayload(computeLatLonBoundingBox(layer.nativeBoundingBox, layer.crs)),
  }))

export const buildStaPayloadData = (data: StaApiFormData): NamedApiPayload => ({
  ...data.baseInfo,
  standard: DEFAULTS_BY_TYPE.sensorthings.standard,
})

export const buildOwsPayload = (data: OwsApiFormData): NamedApiPayload => ({
  ...data.baseInfo,
  standard: DEFAULTS_BY_TYPE['ows'].standard,
  description: data.baseInfo.description || undefined,
})

export const getNativeCRSFromDataSink = (
  dataSinkId: string,
  postgisDataSinks: DataSink[],
  postgisDatastructures: DatastructureVersion[],
): string => {
  const dataSink = postgisDataSinks.find(d => d.id === dataSinkId)
  const datastructure = postgisDatastructures.find(d => d.id === dataSink?.configuration.dataStructureVersion.id)
  const umlClass = datastructure?.styles?.nodes?.[0]?.data?.element as UMLClass | undefined
  return umlClass?.attributes?.find(a => a.meta?.gisInfo?.crs)?.meta?.gisInfo?.crs ?? ''
}
export const mapApiStyleToFormData = (styles: Style[]): StyleFormData[] =>
  styles.map(style => ({
    id: style.id,
    name: style.name,
    sldContent: style.sldContent,
  }))

export const mapFormStyleToPayload = (style: StyleFormData): StyleInput => ({
  name: style.name.trim(),
  sldContent: style.sldContent,
})
