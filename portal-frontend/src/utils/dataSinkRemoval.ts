import {
  DatasetRemovalState,
  DataSinkRemoval,
  DataSinkRemovalInput,
  DataSinkRemovalRequest,
  PipelineSave,
  PlannedApi,
  PlannedDataSink,
} from '@/types/dataSinkRemoval'
import { DATASINK_TYPES, DataSinkType } from '@/types/datasinks'
import { API_STANDARDS, ApiStandard } from '@/types/namedApis'

export const NO_REMOVAL: DataSinkRemoval = { dataSinks: [], layers: [], apis: [] }

/** A removed provisioned Data sink loses its stored data at the next release. */
export const deletesStoredData = (removal: DataSinkRemoval) => removal.dataSinks.some(dataSink => dataSink.provisioned)

const uniqueById = <T extends { id: string }>(items: T[]): T[] => [
  ...new Map(items.map(item => [item.id, item])).values(),
]

export const mergeRemovals = (removals: DataSinkRemoval[]): DataSinkRemoval => ({
  dataSinks: uniqueById(removals.flatMap(removal => removal.dataSinks)),
  layers: uniqueById(removals.flatMap(removal => removal.layers)),
  apis: uniqueById(removals.flatMap(removal => removal.apis)),
})

/** The Data sink types that can hold the data of an API of this standard; none for a standard no Data sink backs. */
const BACKING_DATA_SINK_TYPES: Record<ApiStandard, DataSinkType[]> = {
  [API_STANDARDS.OWS]: [DATASINK_TYPES.POSTGIS],
  [API_STANDARDS.STA]: [DATASINK_TYPES.FROST],
  [API_STANDARDS.CUSTOM]: [],
}

const isDetached = (request: DataSinkRemovalRequest, pipelineId: string, dataSinkId: string) =>
  request.removedLinks.some(link => link.pipelineId === pipelineId && link.dataSinkId === dataSinkId)

const becomesInactive = (dataSink: PlannedDataSink, request: DataSinkRemovalRequest) => {
  if (request.deletedDataSinkIds.includes(dataSink.id)) return true
  const isLinked = dataSink.pipelineIds.length > 0
  return isLinked && dataSink.pipelineIds.every(pipelineId => isDetached(request, pipelineId, dataSink.id))
}

/**
 * What the request removes, computed on the state after it. Mirrors the backend's DataSinkRemovalPlan; both run the
 * cases in api/portal-backend/data-sink-removal-cases.json.
 */
export const planDataSinkRemoval = (input: DataSinkRemovalInput): DataSinkRemoval => {
  const { dataSinks, layers, apis, request } = input
  const deactivated = dataSinks.filter(dataSink => becomesInactive(dataSink, request))
  const deactivatedIds = new Set(deactivated.map(dataSink => dataSink.id))
  const deactivatedTypes = new Set(deactivated.map(dataSink => dataSink.dataSinkType))
  const remaining = dataSinks.filter(dataSink => !deactivatedIds.has(dataSink.id))
  const remainingTypes = new Set(remaining.map(dataSink => dataSink.dataSinkType))

  const losesItsBacking = (api: PlannedApi) => {
    const backingTypes = BACKING_DATA_SINK_TYPES[api.standard]
    return backingTypes.some(type => deactivatedTypes.has(type)) && !backingTypes.some(type => remainingTypes.has(type))
  }
  const removedApis = apis.filter(losesItsBacking)
  const removedApiIds = new Set(removedApis.map(api => api.id))
  const removedLayers = layers.filter(
    layer => deactivatedIds.has(layer.dataSinkId) || (layer.namedApiId !== null && removedApiIds.has(layer.namedApiId)),
  )

  return {
    dataSinks: deactivated.map(dataSink => ({
      id: dataSink.id,
      dataSinkType: dataSink.dataSinkType,
      provisioned: dataSink.provisioned,
    })),
    layers: removedLayers.map(({ id, layerName, title }) => ({ id, layerName, title })),
    apis: removedApis.map(({ id, slug, name, standard }) => ({ id, slug, name, standard })),
  }
}

/** Whether everything the removal takes along is already in the agreed one. */
export const isCoveredBy = (removal: DataSinkRemoval, agreed: DataSinkRemoval) => {
  const agreedIds = new Set([...agreed.dataSinks, ...agreed.layers, ...agreed.apis].map(item => item.id))
  const removedItems = [...removal.dataSinks, ...removal.layers, ...removal.apis]
  return removedItems.every(item => agreedIds.has(item.id))
}

/** Whether the user has to agree before the removal: it deletes stored data, a Layer or an API. */
export const needsConfirmation = (removal: DataSinkRemoval) =>
  deletesStoredData(removal) || removal.layers.length > 0 || removal.apis.length > 0

const afterSave = (dataSink: PlannedDataSink, save: PipelineSave): PlannedDataSink => {
  const otherPipelineIds = dataSink.pipelineIds.filter(pipelineId => pipelineId !== save.pipelineId)
  const pipelineIds = save.dataSinkIds.includes(dataSink.id) ? [...otherPipelineIds, save.pipelineId] : otherPipelineIds
  return { ...dataSink, pipelineIds }
}

/**
 * What each save removes when the saves run in this order, each on the state the previous one left. The state holds
 * the Data sinks a save creates already, without a Pipeline, because they exist before the first Pipeline update.
 */
export const planPipelineSaves = (state: DatasetRemovalState, saves: PipelineSave[]): DataSinkRemoval[] => {
  let current = state
  const removals: DataSinkRemoval[] = []
  for (const save of saves) {
    const linkedDataSinks = current.dataSinks.filter(dataSink => dataSink.pipelineIds.includes(save.pipelineId))
    const unlinkedDataSinks = linkedDataSinks.filter(dataSink => !save.dataSinkIds.includes(dataSink.id))
    const removedLinks = unlinkedDataSinks.map(dataSink => ({ pipelineId: save.pipelineId, dataSinkId: dataSink.id }))
    const removal = planDataSinkRemoval({ ...current, request: { removedLinks, deletedDataSinkIds: [] } })
    removals.push(removal)

    const removedDataSinkIds = new Set(removal.dataSinks.map(dataSink => dataSink.id))
    const removedLayerIds = new Set(removal.layers.map(layer => layer.id))
    const removedApiIds = new Set(removal.apis.map(api => api.id))
    const remainingDataSinks = current.dataSinks.filter(dataSink => !removedDataSinkIds.has(dataSink.id))
    current = {
      dataSinks: remainingDataSinks.map(dataSink => afterSave(dataSink, save)),
      layers: current.layers.filter(layer => !removedLayerIds.has(layer.id)),
      apis: current.apis.filter(api => !removedApiIds.has(api.id)),
    }
  }
  return removals
}

/** What deleting the Pipeline removes. */
export const planPipelineDelete = (state: DatasetRemovalState, pipelineId: string): DataSinkRemoval => {
  const linkedDataSinks = state.dataSinks.filter(dataSink => dataSink.pipelineIds.includes(pipelineId))
  const removedLinks = linkedDataSinks.map(dataSink => ({ pipelineId, dataSinkId: dataSink.id }))
  return planDataSinkRemoval({ ...state, request: { removedLinks, deletedDataSinkIds: [] } })
}
