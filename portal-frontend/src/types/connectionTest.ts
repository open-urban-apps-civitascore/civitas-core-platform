import { DatasourcePatchData } from './datasources'

export const PREVIEW_TRUNCATED_MARKER = '\n[truncated]'

export const CONNECTION_TEST_REASONS = ['INVALID_CONNECTOR', 'SOURCE_ERROR', 'TIMEOUT'] as const

export type ConnectionTestReason = (typeof CONNECTION_TEST_REASONS)[number]

export type ConnectionTestInput = { connector: DatasourcePatchData['connector']; apiCall?: string }

export type ConnectionTestRunVariables = {
  input: ConnectionTestInput
  marker: { isOutdated: boolean }
}

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
