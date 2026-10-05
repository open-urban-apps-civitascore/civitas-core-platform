import { DataSink, DATASINK_TYPES, DataSinkType } from '@/types/datasinks'
import { API_STANDARDS, NamedApi } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

export type PipelineDeleteBlockReason = 'inUseByLayer' | 'lastPostgisWithOws' | 'lastFrostWithSta'

// Sinks of deleted pipelines have no pipelineId and do not count.
const isLastSinkTypeInPipelines = (pipelineId: string, dataSinks: DataSink[], dataSinkType: DataSinkType) => {
  const sinksInPipelines = dataSinks.filter(sink => sink.dataSinkType === dataSinkType && sink.pipelineId)
  return sinksInPipelines.length > 0 && sinksInPipelines.every(sink => sink.pipelineId === pipelineId)
}

export const getPipelineDeleteBlockReason = (
  pipelineId: string,
  dataSinks: DataSink[],
  namedApis: NamedApi[],
): PipelineDeleteBlockReason | null => {
  const isUsedByLayer = dataSinks.some(sink => sink.pipelineId === pipelineId && sink.inUseByLayer)
  if (isUsedByLayer) return 'inUseByLayer'

  const isLastPostgisWithOws =
    hasApiType(namedApis, API_STANDARDS.OWS) && isLastSinkTypeInPipelines(pipelineId, dataSinks, DATASINK_TYPES.POSTGIS)
  if (isLastPostgisWithOws) return 'lastPostgisWithOws'

  const isLastFrostWithSta =
    hasApiType(namedApis, API_STANDARDS.STA) && isLastSinkTypeInPipelines(pipelineId, dataSinks, DATASINK_TYPES.FROST)
  if (isLastFrostWithSta) return 'lastFrostWithSta'

  return null
}
