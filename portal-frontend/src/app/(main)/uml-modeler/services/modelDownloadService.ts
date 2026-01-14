/**
 * Model Download Service
 *
 * Client-side service for downloading UML model files from the backend Model Atlas Service.
 */

import { apiRequest } from '@/app/services/api/request/apiRequest'

export type ModelFormat = 'json' | 'xml' | 'ecore+xml' | 'schema+json'

export interface DownloadResult {
  success: boolean
  data?: string
  message?: string
}

/**
 * Content-Type mapping for different model formats
 */
const formatToAcceptHeader: Record<ModelFormat, string> = {
  json: 'application/json',
  xml: 'application/xml',
  // eslint-disable-next-line @typescript-eslint/naming-convention
  'ecore+xml': 'application/ecore+xml',
  // eslint-disable-next-line @typescript-eslint/naming-convention
  'schema+json': 'application/schema+json',
}

/**
 * Downloads a model from the backend Model Atlas Service
 * @param nsUri - The namespace URI of the model to download
 * @param format - The desired format for the model (default: 'xml')
 */
export const downloadModelFromBackend = async (nsUri: string, format: ModelFormat = 'xml'): Promise<DownloadResult> => {
  try {
    const params = new URLSearchParams()
    params.append('nsUri', nsUri)

    const result = await apiRequest<string>({
      endpoint: '/models/download',
      method: 'GET',
      params,
      headers: {
        Accept: formatToAcceptHeader[format],
      },
      errorMessage: 'Failed to download model from backend.',
    })

    return { success: true, data: result.data, message: 'Model downloaded successfully' }
  } catch (error) {
    console.error('Error downloading model:', error)
    return {
      success: false,
      message: error instanceof Error ? error.message : 'An unknown error occurred',
    }
  }
}
