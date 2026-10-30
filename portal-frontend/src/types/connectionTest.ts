import { DatasourcePatchData } from './datasources'

export const MASKED_SECRET = '********'
export const PREVIEW_TRUNCATED_MARKER = '\n[truncated]'

export type ConnectionTestReason = 'INVALID_CONNECTOR' | 'SOURCE_ERROR' | 'TIMEOUT'

export type ConnectionTestInput = { connector: DatasourcePatchData['connector']; apiCall?: string }

export type ConnectionTestResult = {
  success: boolean
  reason: ConnectionTestReason | null
  message: string | null
  preview: string | null
  previewTruncated: boolean
  rowCount: number | null
  httpStatus: number | null
}

export type ConnectionTestProblem = { code: string; details: string[] }
