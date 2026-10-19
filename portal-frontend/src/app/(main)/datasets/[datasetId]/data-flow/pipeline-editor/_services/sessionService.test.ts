import { describe, expect, it } from 'vitest'

import { everyNodeTypePipeline, savedAs } from '@/test-support/pipelineFixtures'

import { buildPipelinePayload } from './payloadBuilderService'
import { createSessionFromBackendDTO } from './sessionService'

/**
 * The reload path: the editor rehydrates from `styles`, never from the CORE model. These cases send
 * a saved pipeline through JSON and back, which is the part of the reload that needs no server.
 */

describe('createSessionFromBackendDTO', () => {
  const source = everyNodeTypePipeline()
  const reloaded = () => createSessionFromBackendDTO(savedAs(source)).pipeline

  it('restores every node with its type, position and data', () => {
    expect(reloaded().nodes).toEqual(source.nodes)
  })

  it('restores the edges', () => {
    expect(reloaded().edges).toEqual(source.edges)
  })

  it('restores the viewport the pipeline was saved with', () => {
    expect(reloaded().viewport).toEqual({ x: 12, y: -34, zoom: 1.5 })
  })

  it('exports the same CORE model when saved again', () => {
    expect(buildPipelinePayload(reloaded()).model).toEqual(buildPipelinePayload(source).model)
  })
})
