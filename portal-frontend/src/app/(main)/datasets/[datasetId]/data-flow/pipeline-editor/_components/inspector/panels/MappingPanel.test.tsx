import { render, screen } from '@testing-library/react'
import React from 'react'
import { describe, expect, it, vi } from 'vitest'

import { useGetMapping } from '@/app/services/api/mappings/clientRequests'

import type { MappingNodeData } from '../../../_types/nodes'
import { MappingPanel } from './MappingPanel'

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('../../../_hooks/use-pipeline-permissions', () => ({
  usePipelinePermissions: () => ({ canReadDatastructures: false }),
}))

vi.mock('../../../_hooks/use-datastructure-version-info', () => ({
  parseCompositeKey: (key: string) => {
    const parts = key.split('/')
    return parts.length === 2 ? { datastructureId: parts[0], versionId: parts[1] } : null
  },
  useDatastructureVersionInfo: vi.fn(() => ({ name: undefined, versionNumber: undefined })),
}))

vi.mock(
  '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal',
  () => ({ DataModelImportModal: () => null }),
)

vi.mock('@/app/services/api/mappings/clientRequests', () => ({
  useGetMapping: vi.fn(),
}))

// Capture the modal props so tests can assert the read-only wiring without rendering ReactFlow.
const modalPropsRef = vi.hoisted(() => ({ current: null as Record<string, unknown> | null }))

vi.mock('../../mapping-editor/MappingEditorModal', () => ({
  MappingEditorModal: (props: Record<string, unknown>) => {
    modalPropsRef.current = props
    return null
  },
}))

const LOGICAL_URN = 'urn:core:standard:openurbanapps:mapping:environment:luftmessungzuobservation:uktwf8tdur'
const VERSIONED_URN = `${LOGICAL_URN}:1.0.0`

/** The hydrated shape of a bundle-installed mapping node: ref present, editor state empty. */
const installedNode = (): MappingNodeData => ({
  label: 'Luftmessung → Observation',
  configured: true,
  mappingConfig: { fields: {}, positions: {} },
  mappingRef: VERSIONED_URN,
  mappingLogicalUrn: LOGICAL_URN,
})

const stubMappingQuery = (data: unknown) => {
  vi.mocked(useGetMapping).mockReturnValue({ data: data ? { data } : undefined } as unknown as ReturnType<
    typeof useGetMapping
  >)
}

describe('MappingPanel', () => {
  it('renders an installed mapping read-only and fetches it by its logical urn', () => {
    modalPropsRef.current = null
    stubMappingQuery(undefined)

    render(<MappingPanel data={installedNode()} onUpdate={vi.fn()} />)

    expect(vi.mocked(useGetMapping)).toHaveBeenCalledWith(LOGICAL_URN)
    expect(screen.getByText('installedHint')).toBeTruthy()
    // The pickers of the authoring path must not render — selecting would invalidate the node.
    expect(screen.queryByText('selectDatastructure')).toBeNull()
    // Without resolved structure shells the editor stays closed.
    expect((screen.getByText('openMappingEditor').closest('button') as HTMLButtonElement).disabled).toBe(true)
  })

  it('opens the editor read-only with the fetched document once the references resolve', () => {
    modalPropsRef.current = null
    const fields = { '$.result': '$.pm25.value' }
    stubMappingQuery({
      title: 'Luftmessung → Observation',
      source: `urn:core:standard:openurbanapps:datastructure:environment:luftmessung:8vuby3re7d`,
      target: `urn:core:standard:openurbanapps:datastructure:environment:staobservation:rkanst1s7f`,
      fields,
      sourceVersion: { id: 'v-source', dataStructureId: 'd-source', version: '1.0.0' },
      targetVersion: { id: 'v-target', dataStructureId: 'd-target', version: '1.0.0' },
    })

    render(<MappingPanel data={installedNode()} onUpdate={vi.fn()} />)

    expect((screen.getByText('openMappingEditor').closest('button') as HTMLButtonElement).disabled).toBe(false)
    const props = modalPropsRef.current as Record<string, unknown>
    expect(props).not.toBeNull()
    expect(props.isReadOnly).toBe(true)
    expect((props.config as { fields: unknown }).fields).toBe(fields)
    expect(props.source).toMatchObject({ datastructureId: 'd-source', versionId: 'v-source' })
    expect(props.target).toMatchObject({ datastructureId: 'd-target', versionId: 'v-target' })
  })

  it('keeps the authoring path for editor-authored nodes and skips the fetch', () => {
    modalPropsRef.current = null
    stubMappingQuery(undefined)
    const authored: MappingNodeData = {
      label: 'Eigenes Mapping',
      configured: false,
      mappingConfig: { fields: {}, positions: {} },
    }

    render(<MappingPanel data={authored} onUpdate={vi.fn()} />)

    expect(vi.mocked(useGetMapping)).toHaveBeenCalledWith(undefined)
    expect(screen.queryByText('installedHint')).toBeNull()
    expect(screen.getAllByText('selectDatastructure')).toHaveLength(2)
  })

  it('treats a node with a ref AND saved editor fields as editor-authored, not installed', () => {
    modalPropsRef.current = null
    stubMappingQuery(undefined)
    const savedInEditor: MappingNodeData = {
      ...installedNode(),
      mappingConfig: { fields: { '$.result': '$.value' }, positions: {} },
    }

    render(<MappingPanel data={savedInEditor} onUpdate={vi.fn()} />)

    expect(vi.mocked(useGetMapping)).toHaveBeenCalledWith(undefined)
    expect(screen.queryByText('installedHint')).toBeNull()
  })
})
