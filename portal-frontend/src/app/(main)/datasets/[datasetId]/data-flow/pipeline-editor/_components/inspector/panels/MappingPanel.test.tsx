import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import type { DatastructureVersion } from '@/types/datastructures'

import type { MappingNodeData } from '../../../_types/nodes'
import type { MappingConfig } from '../../mapping-editor/_types'
import { emptyMappingConfig } from '../../mapping-editor/_types'
import { MappingPanel } from './MappingPanel'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: () => ({ hasPermission: () => true, hasScopedPermission: () => false }),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: () => ({ data: undefined, isPending: false }),
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useGetDatastructures: () => ({ data: undefined, isFetching: false }),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({ getApiRequestParams: () => new URLSearchParams() }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: vi.fn(),
}))

const SOURCE = {
  datastructureId: '3f2504e0-4f89-11d3-9a0c-0305e82c3301',
  versionId: 'version-1',
  name: 'Sensor Reading',
  versionNumber: '1.0.0',
  field: 'reading',
  urn: 'urn:core:platform:civitas:datastructure:common:SensorReading:vhoo8w924h:1.0.0',
}

const TARGET = {
  datastructureId: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  versionId: 'version-2',
  name: 'Observation',
  versionNumber: '2.1.0',
  field: 'result',
  urn: 'urn:core:platform:civitas:datastructure:common:Observation:waqwrg1ntz:2.1.0',
}

const versionOf = (structure: typeof SOURCE) =>
  ({
    id: structure.versionId,
    version: structure.versionNumber,
    dataStructure: { id: structure.datastructureId, name: structure.name },
    model: {
      $id: 'urn:example',
      title: structure.name,
      type: 'object',
      properties: { thing: { $ref: '#/$defs/Thing' } },
      $defs: { Thing: { type: 'object', properties: { [structure.field]: { type: 'string' } } } },
    },
    styles: null,
  }) as unknown as DatastructureVersion

const mappingNode = (mappingConfig: MappingConfig): MappingNodeData => ({
  label: 'Sensor to Observation',
  configured: true,
  sourceDatastructureId: SOURCE.datastructureId,
  sourceVersionId: SOURCE.versionId,
  targetDatastructureId: TARGET.datastructureId,
  targetVersionId: TARGET.versionId,
  mappingConfig,
})

const openEditorAndApply = async (data: MappingNodeData) => {
  const onUpdate = vi.fn()
  render(<MappingPanel data={data} onUpdate={onUpdate} />)
  await userEvent.click(screen.getByRole('button', { name: 'openMappingEditor' }))
  await userEvent.click(await screen.findByRole('button', { name: 'actions.apply' }))
  return onUpdate.mock.calls[0][0].mappingConfig as MappingConfig
}

beforeEach(() => {
  vi.mocked(useGetDatastructureVersion).mockImplementation(({ datastructureId }) => {
    const structure = datastructureId === TARGET.datastructureId ? TARGET : SOURCE
    return { data: { data: versionOf(structure) } } as unknown as ReturnType<typeof useGetDatastructureVersion>
  })
})

describe('opening the Mapping editor from the Mapping node', () => {
  it('passes the source and target selected on the node to the Mapping editor', async () => {
    const saved = await openEditorAndApply(mappingNode(emptyMappingConfig()))
    expect(saved).toMatchObject({ source: SOURCE.urn, target: TARGET.urn })
  })

  it('opens the Mapping editor with the mapping saved on the node', async () => {
    const fields = { '$.result': '$.reading' }
    const saved = await openEditorAndApply(mappingNode({ fields, positions: {} }))
    expect(saved.fields).toEqual(fields)
  })
})
