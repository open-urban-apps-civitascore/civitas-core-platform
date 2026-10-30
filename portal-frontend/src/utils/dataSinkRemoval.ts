import { DataSink } from '@/types/datasinks'
import { Layer } from '@/types/layers'
import { NamedApi } from '@/types/namedApis'
import { BACKING_DATASINK_TYPE } from '@/utils/namedApis'

export interface DatasetResources {
  dataSinks: DataSink[]
  layers: Layer[]
  apis: NamedApi[]
}

/** What the backend deletes together with the removed Data sinks. */
export interface DataSinkRemoval {
  layers: Layer[]
  apis: NamedApi[]
  /** A removed Data sink is provisioned, so the next release deletes its stored data. */
  hasStoredData: boolean
}

export const getPipelineDataSinkIds = (dataSinks: DataSink[], pipelineId: string): string[] => {
  const pipelineDataSinks = dataSinks.filter(dataSink => dataSink.pipelineIds.includes(pipelineId))
  return pipelineDataSinks.map(dataSink => dataSink.id)
}

export const getDataSinkRemoval = (
  removedDataSinkIds: string[],
  { dataSinks, layers, apis }: DatasetResources,
): DataSinkRemoval => {
  const removedIds = new Set(removedDataSinkIds)
  const removedDataSinks = dataSinks.filter(dataSink => removedIds.has(dataSink.id))
  const remainingDataSinks = dataSinks.filter(dataSink => !removedIds.has(dataSink.id))
  const losesBackingDataSink = (api: NamedApi) => {
    const backingType = BACKING_DATASINK_TYPE[api.standard]
    return !!backingType && remainingDataSinks.every(dataSink => dataSink.dataSinkType !== backingType)
  }
  return {
    layers: layers.filter(layer => removedIds.has(layer.dataSinkId)),
    apis: apis.filter(losesBackingDataSink),
    hasStoredData: removedDataSinks.some(dataSink => dataSink.provisioned === true),
  }
}

export const hasRemovalConsequences = ({ layers, apis, hasStoredData }: DataSinkRemoval) =>
  hasStoredData || layers.length > 0 || apis.length > 0
