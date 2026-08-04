import { isGeoPersistenceNodeData } from '../_types/nodes'
import type { PipelineSession } from '../_types/session'

/** Matches the backend comparison: trimmed and case-insensitive. */
export const normalizeTableName = (tableName: string): string => tableName.trim().toLowerCase()

/** Normalized table name → name of the pipeline using it. */
export type TableNameOwners = Readonly<Record<string, string>>

interface TableNameUsage {
  sessionId: string
  nodeId: string
  pipelineName: string
  tableName: string
}

const nodeUsages = (sessions: readonly PipelineSession[]): TableNameUsage[] =>
  sessions.flatMap(session =>
    session.pipeline.nodes.flatMap(node =>
      isGeoPersistenceNodeData(node.data)
        ? [
            {
              sessionId: session.id,
              nodeId: node.id,
              pipelineName: session.pipeline.name || session.name,
              tableName: node.data.tableName,
            },
          ]
        : [],
    ),
  )

const tableNameOwners = (
  sessions: readonly PipelineSession[],
  isOwnUsage: (usage: TableNameUsage) => boolean,
): TableNameOwners => {
  const owners: Record<string, string> = {}
  for (const usage of nodeUsages(sessions)) {
    const tableName = normalizeTableName(usage.tableName)
    if (isOwnUsage(usage) || tableName === '' || owners[tableName]) continue
    owners[tableName] = usage.pipelineName
  }
  return owners
}

/** The pipelines using each table name outside the given pipeline. */
export const tableNameOwnersOutsideSession = (
  sessions: readonly PipelineSession[],
  sessionId: string | null,
): TableNameOwners => tableNameOwners(sessions, usage => usage.sessionId === sessionId)

/** The pipeline using this table name in any other geo persistence node, or null. */
export const tableNameOwnerOutsideNode = (
  sessions: readonly PipelineSession[],
  nodeId: string,
  tableName: string,
): string | null => {
  const normalized = normalizeTableName(tableName)
  if (normalized === '') return null
  return tableNameOwners(sessions, usage => usage.nodeId === nodeId)[normalized] ?? null
}
