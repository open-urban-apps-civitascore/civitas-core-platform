import { act, renderHook } from '@testing-library/react'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  useStatusUpdateDatastructureVersion,
  useUpdateDatastructureVersion,
  useUpdateDatastructureVersionReleased,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { DATASTRUCTURE_STATUS_TYPES, type DatastructureVersion } from '@/types/datastructures'

import { useDatastructureVersion } from './useDatastructureVersion'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}))

vi.mock('next-intl', () => ({
  useTranslations: (namespace: string) => (key: string, values?: Record<string, string>) =>
    values?.reason ? `${namespace}.${key}|${values.reason}` : `${namespace}.${key}`,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

vi.mock('@/hooks/use-error', () => ({
  useError: () => ({ handleFormValidationError: vi.fn() }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useCreateDatastructureVersion: vi.fn(),
  useStatusUpdateDatastructureVersion: vi.fn(),
  useUpdateDatastructureVersion: vi.fn(),
  useUpdateDatastructureVersionReleased: vi.fn(),
}))

vi.mock('@/components/uml-modeler/hooks/use-multi-session-manager', () => ({
  useMultiSessionManager: vi.fn(),
}))

// A real UUID: buildDataStructureUrn derives the disambiguator by reading the id's hex as a BigInt.
const DS_ID = '11111111-1111-1111-1111-111111111111'

const classNode = (id: string, name: string) => ({
  id: `node-${id}`,
  type: 'class',
  position: { x: 0, y: 0 },
  data: {
    element: {
      id,
      name,
      type: 'class',
      attributes: [{ id: `${id}-a1`, name: 'value', type: 'String', visibility: 'public' }],
      operations: [],
    },
    label: name,
  },
})

/** Two unconnected classes: no unique root, so the schema export refuses to build a model. */
const invalidDiagram = () =>
  ({
    id: 'diagram-1',
    name: 'Struct',
    nodes: [classNode('a', 'Alpha'), classNode('b', 'Beta')],
    edges: [],
    lastModified: new Date(0),
    isDirty: false,
  }) as unknown as UMLDiagram

const validDiagram = () =>
  ({
    id: 'diagram-1',
    name: 'Struct',
    nodes: [classNode('a', 'Alpha')],
    edges: [],
    lastModified: new Date(0),
    isDirty: false,
  }) as unknown as UMLDiagram

const version = (over: Partial<DatastructureVersion> = {}): DatastructureVersion =>
  ({
    id: 'v1',
    version: '1.0.0',
    description: 'desc',
    dataStructureVersionSource: 'OWN',
    dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
    modelName: 'Struct',
    styles: invalidDiagram(),
    model: null,
    ...over,
  }) as unknown as DatastructureVersion

const mutation = () => ({ mutateAsync: vi.fn().mockResolvedValue({ data: version() }), isPending: false })

const setup = (versionData: DatastructureVersion) => {
  const updateVersion = mutation()
  const updateReleased = mutation()
  const createVersion = mutation()
  const updateStatus = mutation()
  vi.mocked(useUpdateDatastructureVersion).mockReturnValue(updateVersion as never)
  vi.mocked(useUpdateDatastructureVersionReleased).mockReturnValue(updateReleased as never)
  vi.mocked(useCreateDatastructureVersion).mockReturnValue(createVersion as never)
  vi.mocked(useStatusUpdateDatastructureVersion).mockReturnValue(updateStatus as never)
  // The session id must match buildSessionFromVersion(version).id (= styles.id) — otherwise the
  // hook's session-closed effect clears the form's nodes and no diagram reaches the save.
  vi.mocked(useMultiSessionManager).mockReturnValue({
    activeSession: {
      id: 'diagram-1',
      name: 'Struct',
      diagram: versionData.styles,
      isDirty: false,
      dirtyFields: new Set(),
      lastModified: new Date(0),
      created: new Date(0),
    },
    activeSessionId: 'diagram-1',
    setSession: vi.fn(),
    markSessionDirty: vi.fn(),
    markFieldClean: vi.fn(),
    getActiveSession: vi.fn(),
  } as never)

  const hook = renderHook(() =>
    useDatastructureVersion({
      datastructureId: DS_ID,
      dataStructureName: 'Struct',
      version: versionData,
      isCreateMode: false,
    }),
  )
  return { hook, updateVersion, updateReleased, updateStatus }
}

describe('useDatastructureVersion — save-flow gating for unexportable diagrams', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('refuses to release a version whose diagram yields no model', async () => {
    const { hook, updateVersion, updateStatus } = setup(version())

    act(() => hook.result.current.handleStatusChange(DATASTRUCTURE_STATUS_TYPES.AVAILABLE))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.releaseInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
    expect(updateStatus.mutateAsync).not.toHaveBeenCalled()
  })

  it('refuses the save even when the version is already released (status not dirty)', async () => {
    const { hook, updateVersion, updateReleased } = setup(
      version({ dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE }),
    )

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.releaseInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
    expect(updateReleased.mutateAsync).not.toHaveBeenCalled()
  })

  it('parks a draft diagram-only, silently, sending model null', async () => {
    const { hook, updateVersion } = setup(version())

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(true)
    expect(toast.warning).not.toHaveBeenCalled()
    expect(updateVersion.mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ model: null }) }),
    )
  })

  it('parks a draft silently even when a model was previously persisted', async () => {
    const { hook } = setup(version({ model: { title: 'Struct' } as never }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    await act(async () => {
      await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(toast.warning).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('passes the exported model through for a resolvable diagram', async () => {
    const { hook, updateVersion } = setup(version({ styles: validDiagram() }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(true)
    expect(toast.warning).not.toHaveBeenCalled()
    const payload = updateVersion.mutateAsync.mock.calls[0][0] as { data: { model: Record<string, unknown> } }
    expect(payload.data.model).toMatchObject({ properties: { Alpha: { $ref: '#/$defs/Alpha' } } })
  })
})
