import proj4 from 'proj4'

import { UMLClass } from '@/components/uml-modeler/types/uml'
import { crsOptions } from '@/const/crs'
import { Datasink } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
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

// The Lat/Lon Bbox refers to the CRS 'EPSG:4326' and is used in the GetCapabilities Geoserver request
// This function transferes the values from the native bbox to lat/lon coordinates
const computeLatLonBoundingBox = (
  nativeBoundingBox: LayerFormData['nativeBoundingBox'],
  crs: string,
): LayerFormData['latLonBoundingBox'] => {
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
  layers.map(layer => ({
    ...layer,
    defaultStyleId: layer.defaultStyleId && layer.defaultStyleId !== 'none' ? layer.defaultStyleId : null,
    nativeBoundingBox: toBoundingBoxPayload(layer.nativeBoundingBox),
    latLonBoundingBox: toBoundingBoxPayload(computeLatLonBoundingBox(layer.nativeBoundingBox, layer.crs)),
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

export const getNativeCRSFromDatasink = (
  datasinkId: string,
  postgisDatasinks: Datasink[],
  postgisDatastructures: DatastructureVersion[],
): string => {
  const datasink = postgisDatasinks.find(d => d.id === datasinkId)
  const datastructure = postgisDatastructures.find(d => d.id === datasink?.configuration.dataStructureVersion.id)
  const umlClass = datastructure?.styles?.nodes[0].data.element as UMLClass | undefined
  return umlClass?.attributes?.find(a => a.meta?.gisInfo?.crs)?.meta?.gisInfo?.crs ?? ''
}
