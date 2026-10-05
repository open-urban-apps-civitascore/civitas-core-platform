import { DataSink, DATASINK_TYPES, DataSinkType } from '@/types/datasinks'
import { API_STANDARDS, NamedApi } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

export type PipelineDeleteBlockReason = 'inUseByLayer' | 'lastPostgisWithOws' | 'lastFrostWithSta'

type HasSinkOfType = (dataSinkType: DataSinkType) => boolean

export interface PipelineSinkChecks {
  hasSinkInPipeline: HasSinkOfType
  hasSinkInOtherPipeline: HasSinkOfType
}

// An unsaved pipeline has no pipelineId and owns no saved sink.
const isSinkOfPipeline = (sink: DataSink, pipelineId: string | undefined) =>
  !!pipelineId && sink.pipelineId === pipelineId

export const getSavedSinkChecks = (pipelineId: string | undefined, dataSinks: DataSink[]): PipelineSinkChecks => ({
  hasSinkInPipeline: dataSinkType =>
    dataSinks.some(sink => isSinkOfPipeline(sink, pipelineId) && sink.dataSinkType === dataSinkType),
  // Sinks of deleted pipelines have no pipelineId and do not count.
  hasSinkInOtherPipeline: dataSinkType =>
    dataSinks.some(sink => sink.dataSinkType === dataSinkType && sink.pipelineId && sink.pipelineId !== pipelineId),
})

export const getPipelineDeleteBlockReason = (
  pipelineId: string | undefined,
  dataSinks: DataSink[],
  namedApis: NamedApi[],
  sinkChecks: PipelineSinkChecks = getSavedSinkChecks(pipelineId, dataSinks),
): PipelineDeleteBlockReason | null => {
  const isUsedByLayer = dataSinks.some(sink => isSinkOfPipeline(sink, pipelineId) && sink.inUseByLayer)
  if (isUsedByLayer) return 'inUseByLayer'

  const isLastSinkOfType = (dataSinkType: DataSinkType) =>
    sinkChecks.hasSinkInPipeline(dataSinkType) && !sinkChecks.hasSinkInOtherPipeline(dataSinkType)

  const isLastPostgisWithOws = hasApiType(namedApis, API_STANDARDS.OWS) && isLastSinkOfType(DATASINK_TYPES.POSTGIS)
  if (isLastPostgisWithOws) return 'lastPostgisWithOws'

  const isLastFrostWithSta = hasApiType(namedApis, API_STANDARDS.STA) && isLastSinkOfType(DATASINK_TYPES.FROST)
  if (isLastFrostWithSta) return 'lastFrostWithSta'

  return null
}
