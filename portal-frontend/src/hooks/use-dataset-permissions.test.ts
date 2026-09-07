import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { AssignmentScope } from '@/types/assignments'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Dataset, DATASET_STATUS_TYPES, DatasetStatusTypes } from '@/types/datasets'

import { useDatasetPermissions, useDatasetPermissionsById } from './use-dataset-permissions'

vi.mock('@/app/services/api/users/clientRequests')
vi.mock('@/app/services/api/datasets/clientRequests')

const DATASET_ID = 'dataset-1'

const ALL_PERMISSIONS: PermissionName[] = [
  PERMISSION_NAMES.DATASET_READ,
  PERMISSION_NAMES.DATASET_CREATE,
  PERMISSION_NAMES.DATASET_UPDATE,
  PERMISSION_NAMES.DATASET_DELETE,
  PERMISSION_NAMES.DATASET_RELEASE,
  PERMISSION_NAMES.DATASOURCE_READ,
  PERMISSION_NAMES.DATASTRUCTURE_READ,
]

type Assignment = { scopeType: AssignmentScope; scopeId: string | null; permissions: PermissionName[] }

const mockAssignments = (assignments: Assignment[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test.user1',
      email: 'test.user1@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments,
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

const mockTenantPermissions = (permissions: PermissionName[]) =>
  mockAssignments([{ scopeType: 'TENANT', scopeId: null, permissions }])

const mockPermissionsLoading = () => {
  vi.mocked(useGetCurrentUser).mockReturnValue({ data: undefined } as ReturnType<typeof useGetCurrentUser>)
}

const makeDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: DATASET_ID,
  name: 'Test Dataset 1',
  description: 'A test dataset',
  dataSetStatus: DATASET_STATUS_TYPES.DRAFT,
  openDataAccess: false,
  pipelines: [],
  datapool: null,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  createdBy: { id: 'user-1', name: 'Test User 1' },
  ...overrides,
})

const renderPermissions = (overrides: Partial<Dataset> = {}) =>
  renderHook(() => useDatasetPermissions(makeDataset(overrides))).result.current

const renderWithoutDataset = () => renderHook(() => useDatasetPermissions(undefined)).result.current

// canReadDatastructures is unscoped, so it is asserted per case rather than in this shape.
const scopedFlagsDenied = {
  canRead: false,
  canRelease: false,
  canEditMetadata: false,
  canViewApis: false,
  canEditApis: false,
  canEditPipeline: false,
  canCreatePipeline: false,
  canDeletePipeline: false,
}

describe('useDatasetPermissions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // Scoped assignments cannot grant DATASTRUCTURE_READ, so canEditApis needs it from a TENANT entry.
  const withDatastructureRead = (assignment: Assignment): Assignment[] => [
    assignment,
    { scopeType: 'TENANT', scopeId: null, permissions: [PERMISSION_NAMES.DATASTRUCTURE_READ] },
  ]

  describe('dataset status', () => {
    it('allows editing, creating and deleting on a DRAFT dataset for a user with all necessary permission', () => {
      mockTenantPermissions(ALL_PERMISSIONS)
      const { canEditApis, canEditPipeline, canCreatePipeline, canDeletePipeline } = renderPermissions()
      expect({ canEditApis, canEditPipeline, canCreatePipeline, canDeletePipeline }).toEqual({
        canEditApis: true,
        canEditPipeline: true,
        canCreatePipeline: true,
        canDeletePipeline: true,
      })
    })

    it.each([DATASET_STATUS_TYPES.READY, DATASET_STATUS_TYPES.AVAILABLE])(
      'denies editing, creating and deleting on a %s dataset even with every permission',
      dataSetStatus => {
        mockTenantPermissions(ALL_PERMISSIONS)
        const { canEditApis, canEditPipeline, canCreatePipeline, canDeletePipeline } = renderPermissions({
          dataSetStatus,
        })
        expect({ canEditApis, canEditPipeline, canCreatePipeline, canDeletePipeline }).toEqual({
          canEditApis: false,
          canEditPipeline: false,
          canCreatePipeline: false,
          canDeletePipeline: false,
        })
      },
    )

    it('keeps the data structure entry points open on a released dataset', () => {
      mockTenantPermissions(ALL_PERMISSIONS)
      expect(renderPermissions({ dataSetStatus: DATASET_STATUS_TYPES.AVAILABLE }).canReadDatastructures).toBe(true)
    })
  })

  describe('scoping', () => {
    it('grants a permission assigned on the dataset itself', () => {
      mockAssignments(
        withDatastructureRead({
          scopeType: 'DATASET',
          scopeId: DATASET_ID,
          permissions: [PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASET_UPDATE],
        }),
      )
      expect(renderPermissions().canEditApis).toBe(true)
    })

    it('denies a permission assigned on a different dataset', () => {
      mockAssignments(
        withDatastructureRead({
          scopeType: 'DATASET',
          scopeId: 'other-dataset',
          permissions: [PERMISSION_NAMES.DATASET_UPDATE],
        }),
      )
      expect(renderPermissions().canEditApis).toBe(false)
    })

    it('grants a dataset permission assigned on the datapool the dataset belongs to', () => {
      mockAssignments(
        withDatastructureRead({
          scopeType: 'DATAPOOL',
          scopeId: 'pool-1',
          permissions: [PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASET_UPDATE],
        }),
      )
      expect(renderPermissions({ datapool: { id: 'pool-1', name: 'Test Pool 1' } }).canEditApis).toBe(true)
    })

    it('denies a dataset permission assigned on a different datapool', () => {
      mockAssignments(
        withDatastructureRead({
          scopeType: 'DATAPOOL',
          scopeId: 'pool-1',
          permissions: [PERMISSION_NAMES.DATASET_UPDATE],
        }),
      )
      expect(renderPermissions({ datapool: { id: 'pool-2', name: 'Test Pool 2' } }).canEditApis).toBe(false)
    })

    it('reads datastructures from an unscoped permission', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASTRUCTURE_READ])
      expect(renderPermissions().canReadDatastructures).toBe(true)
    })
  })

  describe('canEditMetadata', () => {
    it.each([DATASET_STATUS_TYPES.DRAFT, DATASET_STATUS_TYPES.READY])(
      'is true on a %s dataset with DATASET_UPDATE alone',
      dataSetStatus => {
        mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE])
        expect(renderPermissions({ dataSetStatus }).canEditMetadata).toBe(true)
      },
    )

    it('is false on an AVAILABLE dataset without DATASET_RELEASE', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE])
      expect(renderPermissions({ dataSetStatus: DATASET_STATUS_TYPES.AVAILABLE }).canEditMetadata).toBe(false)
    })

    it('is true on an AVAILABLE dataset with DATASET_RELEASE on top', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE, PERMISSION_NAMES.DATASET_RELEASE])
      expect(renderPermissions({ dataSetStatus: DATASET_STATUS_TYPES.AVAILABLE }).canEditMetadata).toBe(true)
    })

    it('is false without DATASET_UPDATE, even with DATASET_RELEASE', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_RELEASE])
      expect(renderPermissions().canEditMetadata).toBe(false)
    })

    it('does not require DATASTRUCTURE_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE])
      const { canEditMetadata, canEditApis } = renderPermissions()
      expect({ canEditMetadata, canEditApis }).toEqual({ canEditMetadata: true, canEditApis: false })
    })
  })

  describe('canEditApis', () => {
    const API_EDIT_PERMISSIONS: PermissionName[] = [
      PERMISSION_NAMES.DATASET_READ,
      PERMISSION_NAMES.DATASET_UPDATE,
      PERMISSION_NAMES.DATASTRUCTURE_READ,
    ]

    it('is true when the dataset may be read and updated and data structures may be read', () => {
      mockTenantPermissions(API_EDIT_PERMISSIONS)
      expect(renderPermissions().canEditApis).toBe(true)
    })

    it.each(API_EDIT_PERMISSIONS)('is false without %s', missing => {
      mockTenantPermissions(API_EDIT_PERMISSIONS.filter(permission => permission !== missing))
      expect(renderPermissions().canEditApis).toBe(false)
    })

    it.each([DATASET_STATUS_TYPES.READY, DATASET_STATUS_TYPES.AVAILABLE])(
      'is false on a %s dataset, even with the full api permission set',
      dataSetStatus => {
        mockTenantPermissions(API_EDIT_PERMISSIONS)
        expect(renderPermissions({ dataSetStatus }).canEditApis).toBe(false)
      },
    )
  })

  describe('canViewApis', () => {
    it('is true with DATASET_READ and DATASTRUCTURE_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASTRUCTURE_READ])
      expect(renderPermissions().canViewApis).toBe(true)
    })

    it.each([DATASET_STATUS_TYPES.READY, DATASET_STATUS_TYPES.AVAILABLE])(
      'stays true on a %s dataset',
      dataSetStatus => {
        mockTenantPermissions([PERMISSION_NAMES.DATASET_READ, PERMISSION_NAMES.DATASTRUCTURE_READ])
        expect(renderPermissions({ dataSetStatus }).canViewApis).toBe(true)
      },
    )

    it('is false without DATASET_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASTRUCTURE_READ])
      expect(renderPermissions().canViewApis).toBe(false)
    })

    it('is false without DATASTRUCTURE_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_READ])
      expect(renderPermissions().canViewApis).toBe(false)
    })
  })

  describe('pipeline flags', () => {
    const PIPELINE_PERMISSIONS: PermissionName[] = [
      PERMISSION_NAMES.DATASET_READ,
      PERMISSION_NAMES.DATASET_UPDATE,
      PERMISSION_NAMES.DATASOURCE_READ,
      PERMISSION_NAMES.DATASTRUCTURE_READ,
    ]

    it('allows editing with the full pipeline permission set', () => {
      mockTenantPermissions(PIPELINE_PERMISSIONS)
      expect(renderPermissions().canEditPipeline).toBe(true)
    })

    it.each(PIPELINE_PERMISSIONS)('denies editing without %s', missing => {
      mockTenantPermissions(PIPELINE_PERMISSIONS.filter(permission => permission !== missing))
      expect(renderPermissions().canEditPipeline).toBe(false)
    })

    it('denies creating without DATASET_CREATE, even when editing is allowed', () => {
      mockTenantPermissions(PIPELINE_PERMISSIONS)
      const { canEditPipeline, canCreatePipeline } = renderPermissions()
      expect({ canEditPipeline, canCreatePipeline }).toEqual({ canEditPipeline: true, canCreatePipeline: false })
    })

    it('allows creating with DATASET_CREATE on top', () => {
      mockTenantPermissions([...PIPELINE_PERMISSIONS, PERMISSION_NAMES.DATASET_CREATE])
      expect(renderPermissions().canCreatePipeline).toBe(true)
    })

    it('denies creating with DATASET_CREATE alone', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_CREATE])
      expect(renderPermissions().canCreatePipeline).toBe(false)
    })

    it('denies deleting without DATASET_DELETE, even when editing is allowed', () => {
      mockTenantPermissions(PIPELINE_PERMISSIONS)
      const { canEditPipeline, canDeletePipeline } = renderPermissions()
      expect({ canEditPipeline, canDeletePipeline }).toEqual({ canEditPipeline: true, canDeletePipeline: false })
    })

    it('needs DATASET_DELETE only, not the rest of the pipeline permission set', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_DELETE])
      expect(renderPermissions().canDeletePipeline).toBe(true)
    })
  })

  describe('canRead and canRelease', () => {
    it('reports canRead from DATASET_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_READ])
      expect(renderPermissions().canRead).toBe(true)
    })

    it('denies canRead without DATASET_READ', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE])
      expect(renderPermissions().canRead).toBe(false)
    })

    it('reports canRelease from DATASET_RELEASE on every status, ungated', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_RELEASE])
      const results = Object.values(DATASET_STATUS_TYPES).map((dataSetStatus: DatasetStatusTypes) => [
        dataSetStatus,
        renderPermissions({ dataSetStatus }).canRelease,
      ])
      expect(results).toEqual([
        [DATASET_STATUS_TYPES.DRAFT, true],
        [DATASET_STATUS_TYPES.READY, true],
        [DATASET_STATUS_TYPES.AVAILABLE, true],
      ])
    })

    it('denies canRelease without DATASET_RELEASE', () => {
      mockTenantPermissions([PERMISSION_NAMES.DATASET_UPDATE])
      expect(renderPermissions().canRelease).toBe(false)
    })
  })

  describe('loading', () => {
    it('denies everything while the current user is still loading', () => {
      mockPermissionsLoading()
      const { isDraft, canReadDatastructures, ...scopedFlags } = renderPermissions()
      expect(scopedFlags).toEqual(scopedFlagsDenied)
      expect({ isDraft, canReadDatastructures }).toEqual({ isDraft: true, canReadDatastructures: false })
    })

    it('denies every dataset-scoped flag without a dataset, even with every permission', () => {
      mockTenantPermissions(ALL_PERMISSIONS)
      const { isDraft, canReadDatastructures, ...scopedFlags } = renderWithoutDataset()
      expect(scopedFlags).toEqual(scopedFlagsDenied)
      expect({ isDraft, canReadDatastructures }).toEqual({ isDraft: false, canReadDatastructures: true })
    })
  })
})

describe('useDatasetPermissionsById', () => {
  const mockDataset = (overrides: Partial<Dataset> = {}) =>
    vi.mocked(useGetDataset).mockReturnValue({
      data: { data: makeDataset(overrides) },
      isPending: false,
    } as ReturnType<typeof useGetDataset>)

  const mockDatasetLoading = () =>
    vi.mocked(useGetDataset).mockReturnValue({ data: undefined, isPending: true } as ReturnType<typeof useGetDataset>)

  const mockDatasetUnavailable = () =>
    vi.mocked(useGetDataset).mockReturnValue({ data: undefined, isPending: false } as ReturnType<typeof useGetDataset>)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('denies editing while the dataset is still loading, even with every permission', () => {
    mockTenantPermissions(ALL_PERMISSIONS)
    mockDatasetLoading()
    const { result } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect(result.current.canEditApis).toBe(false)
  })

  it('reports isLoading while the dataset is pending, alongside the locked flags', () => {
    mockTenantPermissions(ALL_PERMISSIONS)
    mockDatasetLoading()
    const { result } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect({ isLoading: result.current.isLoading, canEditApis: result.current.canEditApis }).toEqual({
      isLoading: true,
      canEditApis: false,
    })
  })

  it('clears isLoading but keeps the flags locked when the dataset could not be fetched', () => {
    mockTenantPermissions(ALL_PERMISSIONS)
    mockDatasetUnavailable()
    const { result } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    const { isLoading, isDraft, canReadDatastructures, ...scopedFlags } = result.current
    expect(scopedFlags).toEqual(scopedFlagsDenied)
    expect({ isLoading, isDraft, canReadDatastructures }).toEqual({
      isLoading: false,
      isDraft: false,
      canReadDatastructures: true,
    })
  })

  it('requests the dataset it was given the id of', () => {
    mockTenantPermissions(ALL_PERMISSIONS)
    mockDataset()
    renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect(useGetDataset).toHaveBeenCalledWith({ id: DATASET_ID })
  })

  it('clears isLoading once the dataset has arrived', () => {
    mockTenantPermissions(ALL_PERMISSIONS)
    mockDataset()
    const { result } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect({ isLoading: result.current.isLoading, canEditApis: result.current.canEditApis }).toEqual({
      isLoading: false,
      canEditApis: true,
    })
  })

  it('allows editing once both the dataset and the permissions have loaded', () => {
    mockPermissionsLoading()
    mockDatasetLoading()
    const { result, rerender } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect(result.current.canEditApis).toBe(false)

    mockTenantPermissions(ALL_PERMISSIONS)
    mockDataset()
    rerender()
    expect(result.current.canEditApis).toBe(true)
  })

  it('derives every flag from the fetched dataset, down to its datapool', () => {
    mockAssignments([
      {
        scopeType: 'DATAPOOL',
        scopeId: 'pool-1',
        permissions: [
          PERMISSION_NAMES.DATASET_READ,
          PERMISSION_NAMES.DATASET_CREATE,
          PERMISSION_NAMES.DATASET_UPDATE,
          PERMISSION_NAMES.DATASET_DELETE,
          PERMISSION_NAMES.DATASET_RELEASE,
        ],
      },
      {
        scopeType: 'TENANT',
        scopeId: null,
        permissions: [PERMISSION_NAMES.DATASOURCE_READ, PERMISSION_NAMES.DATASTRUCTURE_READ],
      },
    ])
    mockDataset({ datapool: { id: 'pool-1', name: 'Test Pool 1' } })
    const { result } = renderHook(() => useDatasetPermissionsById(DATASET_ID))
    expect(result.current).toEqual({
      isDraft: true,
      canRead: true,
      canRelease: true,
      canReadDatastructures: true,
      canEditMetadata: true,
      canViewApis: true,
      canEditApis: true,
      canEditPipeline: true,
      canCreatePipeline: true,
      canDeletePipeline: true,
      isLoading: false,
    })
  })
})
