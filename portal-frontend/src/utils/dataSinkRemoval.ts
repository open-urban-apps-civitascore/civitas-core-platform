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

/** What the backend removes with a request it refused as unconfirmed (`REMOVAL_NOT_CONFIRMED`). */
export interface DataSinkRemoval {
  dataSinks: RemovedDataSink[]
  layers: RemovedLayer[]
  apis: RemovedApi[]
}

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
