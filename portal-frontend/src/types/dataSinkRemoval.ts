import { DataSinkType } from '@/types/datasinks'
import { ApiStandard } from '@/types/namedApis'

export interface RemovedDataSink {
  id: string
  dataSinkType: DataSinkType
  provisioned: boolean
}

export interface RemovedLayer {
  id: string
  layerName: string
  title: string | null
}

export interface RemovedApi {
  id: string
  slug: string
  name: string
  standard: ApiStandard
}

/**
 * What a request removes from its Dataset: calculated by the portal before the request, or named by the backend when it
 * refuses one as unconfirmed (`REMOVAL_NOT_CONFIRMED`).
 */
export interface DataSinkRemoval {
  dataSinks: RemovedDataSink[]
  layers: RemovedLayer[]
  apis: RemovedApi[]
}

/** An active Data sink of the Dataset, with the Pipelines that write into it. */
export interface PlannedDataSink {
  id: string
  dataSinkType: DataSinkType
  provisioned: boolean
  pipelineIds: string[]
}

export interface PlannedLayer {
  id: string
  dataSinkId: string
  namedApiId: string | null
  layerName: string
  title: string | null
}

export interface PlannedApi {
  id: string
  slug: string
  name: string
  standard: ApiStandard
}

export interface PipelineDataSinkLink {
  pipelineId: string
  dataSinkId: string
}

/** The removals one request makes: links it removes, also by deleting a Pipeline, and Data sinks it deletes. */
export interface DataSinkRemovalRequest {
  removedLinks: PipelineDataSinkLink[]
  deletedDataSinkIds: string[]
}

export interface DataSinkRemovalInput {
  dataSinks: PlannedDataSink[]
  layers: PlannedLayer[]
  apis: PlannedApi[]
  request: DataSinkRemovalRequest
}

export interface DatasetRemovalState {
  dataSinks: PlannedDataSink[]
  layers: PlannedLayer[]
  apis: PlannedApi[]
}

/** A Pipeline save that links the Pipeline to exactly these Data sinks. */
export interface PipelineSave {
  pipelineId: string
  dataSinkIds: string[]
}
