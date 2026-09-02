import { act, renderHook } from '@testing-library/react'
import { toast } from 'sonner'

import { BREADCRUMB_QUERY_KEY } from '@/app/services/api/breadcrumbs/clientRequests'
import {
  useCreateDatastructureVersion,
  useStatusUpdateDatastructureVersion,
  useUpdateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { DATASTRUCTURE_STATUS_TYPES, type DatastructureVersion } from '@/types/datastructures'

import { useDatastructureVersion } from './useDatastructureVersion'

const { mockRefresh, mockInvalidateQueries } = vi.hoisted(() => ({
  mockRefresh: vi.fn(),
  mockInvalidateQueries: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: mockRefresh }),
}))

vi.mock('@tanstack/react-query', async importOriginal => ({
  ...(await importOriginal<typeof import('@tanstack/react-query')>()),
  useQueryClient: () => ({ invalidateQueries: mockInvalidateQueries }),
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

/** The same two unconnected classes, but with one designated as root — a savable staged state. */
const designatedRootDiagram = () => {
  const alpha = classNode('a', 'Alpha')
  ;(alpha.data.element as { isRoot?: boolean }).isRoot = true
  return {
    id: 'diagram-1',
    name: 'Struct',
    nodes: [alpha, classNode('b', 'Beta')],
    edges: [],
    lastModified: new Date(0),
    isDirty: false,
  } as unknown as UMLDiagram
}

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
  const createVersion = mutation()
  const updateStatus = mutation()
  vi.mocked(useUpdateDatastructureVersion).mockReturnValue(updateVersion as never)
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
  return { hook, updateVersion, updateStatus }
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
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.saveInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
    expect(updateStatus.mutateAsync).not.toHaveBeenCalled()
  })

  it('refuses the save even when the version is already released (status not dirty)', async () => {
    const { hook, updateVersion } = setup(version({ dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.saveInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
  })

  it('refuses a draft too, rather than parking it with a null model', async () => {
    const { hook, updateVersion } = setup(version())

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.saveInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
  })

  it('refuses a draft even when a model was previously persisted', async () => {
    const { hook, updateVersion } = setup(version({ model: { title: 'Struct' } as never }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    await act(async () => {
      await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('errors.saveInvalidModel'))
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
  })

  it('saves unconnected classes once one of them is designated as root', async () => {
    const { hook, updateVersion } = setup(version({ styles: designatedRootDiagram() }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(true)
    expect(toast.error).not.toHaveBeenCalled()

    // Saved under a DataStructure URN → the canonical $defs library: Alpha is the document root,
    // designated by a top-level $ref equal to its own $defs member $id (no inline `properties`).
    // Beta stays in $defs, unreferenced but preserved.
    const payload = updateVersion.mutateAsync.mock.calls[0][0] as {
      data: { model: { $ref?: string; $defs?: Record<string, { $id?: string }> } }
    }
    expect(payload.data.model.$ref).toBe(payload.data.model.$defs?.Alpha?.$id)
    expect(payload.data.model.$defs).toHaveProperty('Beta')
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
    // Saved under a DataStructure URN → the canonical $defs library: the root class Alpha is a $defs
    // member stamped with its Element URN as $id, designated by a top-level $ref to that same URN
    // (no inline root `properties`). Model Forge splits the members into Elements on ingest.
    const model = payload.data.model as { $ref?: string; $defs?: Record<string, { $id?: string }> }
    expect(model.$defs?.Alpha).toBeDefined()
    expect(model.$ref).toBe(model.$defs?.Alpha?.$id)
  })
})

describe('useDatastructureVersion — released versions take no field update', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('refuses a field update on a released version instead of sending a request', async () => {
    const { hook, updateVersion, updateStatus } = setup(
      version({ dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE, styles: validDiagram() }),
    )

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledExactlyOnceWith('datastructureVersions.messages.isAvailableModelHint')
    expect(updateVersion.mutateAsync).not.toHaveBeenCalled()
    expect(updateStatus.mutateAsync).not.toHaveBeenCalled()
  })
})

describe('useDatastructureVersion — status-dependent field validation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('flags an emptied description as required while the status is AVAILABLE', async () => {
    const { hook } = setup(version({ dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE }))

    await act(async () => {
      hook.result.current.form.setValue('description', '', { shouldValidate: true })
    })

    expect(hook.result.current.form.getFieldState('description').error?.message).toBe('common.errors.required')
  })

  it('leaves an emptied description unflagged while the status is DRAFT', async () => {
    const { hook } = setup(version())

    await act(async () => {
      hook.result.current.form.setValue('description', '', { shouldValidate: true })
    })

    expect(hook.result.current.form.getFieldState('description').error).toBeUndefined()
  })

  it('flags the empty description as soon as the status switches to AVAILABLE', async () => {
    const { hook } = setup(version({ description: '' }))

    expect(hook.result.current.form.getFieldState('description').error).toBeUndefined()

    await act(async () => {
      hook.result.current.handleStatusChange(DATASTRUCTURE_STATUS_TYPES.AVAILABLE)
    })

    expect(hook.result.current.form.getFieldState('description').error?.message).toBe('common.errors.required')
  })

  it('clears the required error when the status switches back to DRAFT', async () => {
    const { hook } = setup(
      version({ description: '', dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE }),
    )

    await act(async () => {
      hook.result.current.form.setValue('description', '', { shouldValidate: true })
    })
    expect(hook.result.current.form.getFieldState('description').error?.message).toBe('common.errors.required')

    await act(async () => {
      hook.result.current.handleStatusChange(DATASTRUCTURE_STATUS_TYPES.DRAFT)
    })

    expect(hook.result.current.form.getFieldState('description').error).toBeUndefined()
  })
})

describe('useDatastructureVersion — a stored model reaches the surfaces outside the form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reloads the route and the breadcrumb after a draft update assigns a version number', async () => {
    const { hook } = setup(version({ styles: validDiagram(), version: null }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    await act(async () => {
      await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    // The heading renders on the server and the breadcrumb has its own query, so both need a
    // reload to show the assigned number.
    expect(mockRefresh).toHaveBeenCalled()
    expect(mockInvalidateQueries).toHaveBeenCalledWith(expect.objectContaining({ queryKey: [BREADCRUMB_QUERY_KEY] }))
  })
})
