import { act, renderHook } from '@testing-library/react'
import { toast } from 'sonner'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import {
  useCreateDatastructureVersion,
  useStatusUpdateDatastructureVersion,
  useUpdateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { rel } from '@/test-support/datastructureFixtures'
import { DATASTRUCTURE_STATUS_TYPES, type DatastructureVersion } from '@/types/datastructures'

import { useDatastructureVersion } from './useDatastructureVersion'

const { mockRefresh } = vi.hoisted(() => ({ mockRefresh: vi.fn() }))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: mockRefresh }),
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

/** validDiagram with the one class designated, rather than left to derivation. */
const rootedSingleClassDiagram = () => {
  const diagram = validDiagram()
  ;(diagram.nodes[0].data.element as { isRoot?: boolean }).isRoot = true
  return diagram
}

/** A one-Element structure whose Element is an enumeration, not a class. */
const singleEnumDiagram = () =>
  ({
    id: 'diagram-1',
    name: 'Struct',
    nodes: [
      {
        id: 'node-s',
        type: 'enumeration',
        position: { x: 0, y: 0 },
        data: {
          element: {
            id: 's',
            name: 'Status',
            type: 'enumeration',
            literals: [
              { id: 'l1', name: 'ACTIVE' },
              { id: 'l2', name: 'INACTIVE' },
            ],
          },
          label: 'Status',
        },
      },
    ],
    edges: [],
    lastModified: new Date(0),
    isDirty: false,
  }) as unknown as UMLDiagram

/** Two classes composing each other: every class is embedded, so the diagram has no root. */
const rootlessDiagram = () =>
  ({
    id: 'diagram-1',
    name: 'Struct',
    nodes: [classNode('a', 'Alpha'), classNode('b', 'Beta')],
    edges: [rel('ab', 'composition', 'a', 'b'), rel('ba', 'composition', 'b', 'a')],
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

  it.each([
    ['several root candidates', invalidDiagram(), 'ambiguousRoot'],
    ['no root', rootlessDiagram(), 'noRoot'],
  ])('refuses to release a version whose diagram has %s and names that reason', async (_label, styles, reason) => {
    const { hook, updateVersion, updateStatus } = setup(version({ styles }))

    act(() => hook.result.current.handleStatusChange(DATASTRUCTURE_STATUS_TYPES.AVAILABLE))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(false)
    expect(toast.error).toHaveBeenCalledWith(
      `datastructureVersions.errors.saveInvalidModel|umlModeler.rootValidation.${reason}`,
    )
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

    // Alpha is the root referenced in $ref, Beta stays in $defs, unreferenced.
    const payload = updateVersion.mutateAsync.mock.calls[0][0] as {
      data: { model: { $ref?: string; $defs?: Record<string, { $id?: string }> } }
    }
    expect(payload.data.model.$ref).toBe(payload.data.model.$defs?.Alpha?.$id)
    expect(payload.data.model.$defs).toHaveProperty('Beta')
  })

  it('sends the exported model for a single class that becomes the root by derivation', async () => {
    const { hook, updateVersion } = setup(version({ styles: validDiagram() }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(true)
    expect(toast.warning).not.toHaveBeenCalled()
    const payload = updateVersion.mutateAsync.mock.calls[0][0] as { data: { model: Record<string, unknown> } }
    // The root class sits in $defs, a top-level $ref points at its $id
    const model = payload.data.model as { $ref?: string; $defs?: Record<string, { $id?: string }> }
    expect(model.$defs?.Alpha).toBeDefined()
    expect(model.$ref).toBe(model.$defs?.Alpha?.$id)
  })

  it('marking the only class as root does not change the saved model', async () => {
    const modelOf = async (diagram: UMLDiagram) => {
      const { hook, updateVersion } = setup(version({ styles: diagram }))
      act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
      let saved: boolean | undefined
      await act(async () => {
        saved = await hook.result.current.saveDatastructureVersion(DS_ID)
      })
      expect(saved).toBe(true)
      const payload = updateVersion.mutateAsync.mock.calls[0][0] as { data: { model: Record<string, unknown> } }
      return payload.data.model as { $ref?: string; $defs?: Record<string, { $id?: string }> }
    }

    const designated = await modelOf(rootedSingleClassDiagram())
    vi.clearAllMocks()
    const derived = await modelOf(validDiagram())

    expect(Object.keys(designated.$defs ?? {})).toEqual(['Alpha'])
    expect(designated.$ref).toBe(designated.$defs?.Alpha?.$id)
    expect(designated).toEqual(derived)
  })

  it('sends the exported model for a one-element structure holding an enumeration', async () => {
    const { hook, updateVersion } = setup(version({ styles: singleEnumDiagram() }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    let saved: boolean | undefined
    await act(async () => {
      saved = await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(saved).toBe(true)
    const payload = updateVersion.mutateAsync.mock.calls[0][0] as { data: { model: Record<string, unknown> } }
    const model = payload.data.model as {
      $ref?: string
      type?: string
      $defs?: Record<string, { $id?: string; enum?: string[] }>
    }

    expect(Object.keys(model.$defs ?? {})).toEqual(['Status'])
    expect(model.$defs?.Status.enum).toEqual(['ACTIVE', 'INACTIVE'])
    expect(model.$ref).toBe(model.$defs?.Status.$id)
    expect(model.type).toBeUndefined()
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

describe('useDatastructureVersion — a version still in use', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('names the reason when a data source or data storage still uses the version', async () => {
    const { hook, updateVersion } = setup(version({ styles: validDiagram() }))
    updateVersion.mutateAsync.mockRejectedValue(
      mockApiError(
        409,
        'Cannot modify DataStructureVersion because it is referenced by one or more DataSources or DataSinks.',
        'urn:civitas:error:RESOURCE_IN_USE',
      ),
    )

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    await act(async () => {
      await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    expect(toast.error).toHaveBeenCalledWith('datastructureVersions.errors.inUseError')
  })
})

describe('useDatastructureVersion — a stored model reaches the surfaces outside the form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reloads the route after a draft update assigns a version number', async () => {
    const { hook } = setup(version({ styles: validDiagram(), version: null }))

    act(() => hook.result.current.form.setValue('description', 'changed', { shouldDirty: true }))
    await act(async () => {
      await hook.result.current.saveDatastructureVersion(DS_ID)
    })

    // The heading renders on the server, so it needs a reload to show the assigned number.
    expect(mockRefresh).toHaveBeenCalled()
  })
})
