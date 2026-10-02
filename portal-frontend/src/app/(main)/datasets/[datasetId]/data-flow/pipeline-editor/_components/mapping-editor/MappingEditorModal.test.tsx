import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import type { DatastructureVersion } from '@/types/datastructures'

import { emptyMappingConfig } from './_types'
import { MappingEditorModal } from './MappingEditorModal'

/**
 * The only place the frontend builds the source and target DataStructure URNs. The expected URNs are
 * written out rather than derived, so the test cannot agree with a wrong builder.
 */

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: vi.fn(),
}))

const SOURCE = {
  datastructureId: '3f2504e0-4f89-11d3-9a0c-0305e82c3301',
  versionId: 'version-1',
  name: 'Sensor Reading',
  versionNumber: '1.0.0',
  urn: 'urn:core:platform:civitas:datastructure:common:SensorReading:vhoo8w924h:1.0.0',
}

const TARGET = {
  datastructureId: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  versionId: 'version-2',
  name: 'Observation',
  versionNumber: '2.1.0',
  urn: 'urn:core:platform:civitas:datastructure:common:Observation:waqwrg1ntz:2.1.0',
}

const versionOf = (structure: typeof SOURCE, field: string) =>
  ({
    id: structure.versionId,
    version: structure.versionNumber,
    dataStructure: { id: structure.datastructureId, name: structure.name },
    model: {
      $id: 'urn:example',
      title: structure.name,
      type: 'object',
      properties: { thing: { $ref: '#/$defs/Thing' } },
      $defs: { Thing: { type: 'object', properties: { [field]: { type: 'string' } } } },
    },
    styles: null,
  }) as unknown as DatastructureVersion

const renderModal = (onSave = vi.fn()) => {
  render(
    <MappingEditorModal
      open
      onOpenChange={vi.fn()}
      name="Sensor to Observation"
      source={{ datastructureId: SOURCE.datastructureId, versionId: SOURCE.versionId, name: SOURCE.name }}
      target={{ datastructureId: TARGET.datastructureId, versionId: TARGET.versionId, name: TARGET.name }}
      config={emptyMappingConfig()}
      onSave={onSave}
    />,
  )
  return onSave
}

const apply = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'actions.apply' }))
}

beforeEach(() => {
  vi.mocked(useGetDatastructureVersion).mockImplementation(({ datastructureId }) => {
    const structure = datastructureId === SOURCE.datastructureId ? SOURCE : TARGET
    const field = structure === SOURCE ? 'reading' : 'result'
    return { data: { data: versionOf(structure, field) } } as unknown as ReturnType<typeof useGetDatastructureVersion>
  })
})

describe('the DataStructure URNs the Mapping editor writes', () => {
  it('names the source DataStructure by its versioned CORE URN', async () => {
    const onSave = renderModal()
    await apply()
    expect(onSave.mock.calls[0][0]).toMatchObject({ source: SOURCE.urn })
  })

  it('names the target DataStructure by its versioned CORE URN', async () => {
    const onSave = renderModal()
    await apply()
    expect(onSave.mock.calls[0][0]).toMatchObject({ target: TARGET.urn })
  })
})
