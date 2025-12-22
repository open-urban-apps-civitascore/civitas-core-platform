/**
 * Model Upload Service
 *
 * Client-side service for uploading UML model files to the backend Model Atlas Service.
 */

import type { UMLDiagram } from '../types/diagram'

import { exportToXmi, sanitizeName } from './xmiExportService'

export interface UploadResult {
  success: boolean
  message?: string
}

/**
 * Uploads a UML diagram to the backend Model Atlas Service
 */
export const uploadModelToBackend = async (diagram: UMLDiagram): Promise<UploadResult> => {
  try {
    const xmiContent = exportToXmi(diagram)
    const sanitizedName = sanitizeName(diagram.name)
    const nsUri = `http://civitas.org/model/${sanitizedName}`
    const filename = `${sanitizedName}.xmi`

    // Create a Blob from the XMI content
    const blob = new Blob([xmiContent], { type: 'application/xml' })

    // Create FormData for multipart upload
    const formData = new FormData()
    formData.append('modelFile', blob, filename)
    formData.append('nsUri', nsUri)

    const response = await fetch('/api/models/upload', {
      method: 'POST',
      body: formData,
      credentials: 'include',
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`Upload failed with status ${response.status}: ${errorText}`)
    }

    return { success: true, message: 'Model uploaded successfully' }
  } catch (error) {
    console.error('Error uploading model:', error)
    return {
      success: false,
      message: error instanceof Error ? error.message : 'An unknown error occurred',
    }
  }
}
