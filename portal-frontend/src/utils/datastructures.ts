import { versionToSchemaTree } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/schema/versionTree'
import { diagramFromJsonSchema, SchemaImportError } from '@/components/uml-modeler/services/jsonSchemaImportService'
import { withCurrentTypes } from '@/components/uml-modeler/services/legacyTypes'
import { createEmptySession } from '@/components/uml-modeler/services/sessionService'
import { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { DirtyField } from '@/components/uml-modeler/types/session'
import { SelectOption } from '@/types/common'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureFormData,
  DatastructuresListData,
  DatastructureStatusType,
  DatastructureVersion,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionPutData,
  DatastructureVersionsListData,
  DatastructureVersionSummary,
} from '@/types/datastructures'

export const mapDatastructureApiToFormData = (datastructure: Datastructure): DatastructureFormData => ({
  id: datastructure.id,
  name: datastructure.name ?? '',
  description: datastructure.description ?? '',
})

// A version without a stored model has no number yet, so it counts as newer than every numbered one.
const sortVersionsDesc = (versions: DatastructureVersionSummary[]): DatastructureVersionSummary[] =>
  [...versions].sort((a, b) => {
    if (!a.version && !b.version) return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    if (!a.version) return -1
    if (!b.version) return 1
    return b.version.localeCompare(a.version, undefined, { numeric: true })
  })

export const mapDatastructuresApiToListData = (datastructures: Datastructure[]): DatastructuresListData[] => {
  return datastructures.map(datastructure => {
    const highestVersion: DatastructureVersionSummary | null =
      datastructure.dataStructureVersions.reduce<DatastructureVersionSummary | null>((highest, current) => {
        // A version with no stored model has no number, so it has no place in the ordering.
        if (!current.version) return highest
        if (!highest?.version) return current
        return current.version.localeCompare(highest.version, undefined, { numeric: true }) > 0 ? current : highest
      }, null)
    const versionCountByStatus = datastructure.dataStructureVersions.reduce<Record<DatastructureStatusType, number>>(
      (counts, version) => ({
        ...counts,
        [version.dataStructureVersionStatus]: counts[version.dataStructureVersionStatus] + 1,
      }),
      { [DATASTRUCTURE_STATUS_TYPES.AVAILABLE]: 0, [DATASTRUCTURE_STATUS_TYPES.DRAFT]: 0 },
    )
    const inUseByReleasedVersions = datastructure.dataStructureVersions.filter(version => version.inUseByReleased)
    return {
      id: datastructure.id,
      dataStructureId: datastructure.id,
      name: datastructure.name,
      description: datastructure.description || '-',
      status: null,
      versionNumber: highestVersion?.version ?? null,
      source: highestVersion?.dataStructureVersionSource || null,
      inUse: datastructure.inUse,
      inUseByReleased: datastructure.inUseByReleased,
      versionCountByStatus,
      inUseByReleasedVersionCount: inUseByReleasedVersions.length,
      // add versions field to versions for showing subrows in table
      versions: sortVersionsDesc(datastructure.dataStructureVersions).map(version => ({
        id: version.id,
        versionNumber: version.version,
        modelUrn: version.modelUrn,
        name: version.version ? `Version ${version.version}` : '-',
        description: version.description || '-',
        status: version.dataStructureVersionStatus,
        source: version.dataStructureVersionSource,
        inUse: version.inUse,
        inUseByReleased: version.inUseByReleased,
        versions: [],
      })),
    }
  })
}

export const mapDatastructureVersionsApiToListData = (
  versions: DatastructureVersionSummary[],
): DatastructureVersionsListData[] =>
  sortVersionsDesc(versions).map(version => ({
    id: version.id,
    versionNumber: version.version,
    name: version.version ? `Version ${version.version}` : '-',
    description: version.description || '-',
    status: version.dataStructureVersionStatus,
    source: version.dataStructureVersionSource,
    inUse: version.inUse,
    inUseByReleased: version.inUseByReleased,
  }))

export const mapDatastructureVersionApiToFormData = (version: DatastructureVersion): DatastructureVersionFormData => ({
  id: version.id,
  version: version.version ?? '',
  description: version.description || '',
  dataStructureVersionStatus: version.dataStructureVersionStatus,
  dataStructureVersionSource: version.dataStructureVersionSource,
  modelName: version.modelName,
  // The nodes the editor opens, so a type retired since the save does not set the form apart from them.
  nodes: (version.styles && withCurrentTypes(version.styles).nodes) || [],
  edges: version.styles?.edges || [],
})

export const mapDatastructureVersionFormToApiData = (
  version: DatastructureVersionFormData,
  sessionDiagram: UMLDiagram | null,
  model: Record<string, unknown> | null,
  existingImportedStructureUrns: string[] = [],
): DatastructureVersionPutData => {
  return {
    id: version.id,
    description: version.description,
    dataStructureVersionStatus: version.dataStructureVersionStatus,
    modelName: version.modelName,
    model,
    styles: sessionDiagram,
    // Derived from the diagram, like the model document: the import happens there, and this makes
    // the provenance readable without opening the editor.
    // A save without a diagram changes no content, so it keeps the pins the version has.
    importedStructureUrns: sessionDiagram
      ? (sessionDiagram.importedStructures ?? []).map(structure => structure.urn)
      : existingImportedStructureUrns,
  }
}

export const parseDatastructureVersionFormData = (values: DatastructureVersionFormData, isDraftMode: boolean) => {
  return isDraftMode
    ? DatastructureVersionFormDraftSchema.safeParse(values)
    : DatastructureVersionFormAvailableSchema.safeParse(values)
}

export const containsNonStatusField = (fieldsToUpdate: Partial<DatastructureVersionFormData>) =>
  Object.keys(fieldsToUpdate).some(key => key !== 'dataStructureVersionStatus')

/**
 * The diagram of a version, drawn or derived.
 *
 * A version has a drawing only where someone drew one. A structure that came in as a document —
 * over the API, or generated from a Data source — carries a model and no diagram, and would open
 * on an empty canvas although its content is right there. The model is read into a diagram for
 * that case, with the layout the standard import uses.
 *
 * A model the reader does not understand leaves the canvas empty, as before: showing a guess of a
 * structure would be worse than showing none.
 */
const diagramOfVersion = (versionData: DatastructureVersion | null): UMLDiagram | null => {
  // A saved diagram carries a node array, even an empty one: a modeller may save a blank canvas,
  // and that is a drawing too. What a document-born version carries instead is no array at all.
  const drawn = versionData?.styles
  if (drawn && Array.isArray(drawn.nodes)) return withCurrentTypes(drawn)

  const model = versionData?.model
  if (!model) return null
  try {
    return diagramFromJsonSchema(model, versionData?.modelName ?? undefined)
  } catch (error) {
    if (!(error instanceof SchemaImportError)) throw error
    console.warn('data structure: the stored model cannot be read into a diagram', error)
    return null
  }
}

export const buildSessionFromVersion = (
  versionData: DatastructureVersion | null,
  sessionId?: string,
  created?: Date,
) => {
  const diagram = diagramOfVersion(versionData)
  const modelName = versionData?.modelName || null
  const fallbackSession = createEmptySession(modelName || undefined)

  return {
    id: sessionId || diagram?.id || fallbackSession.id,
    name: modelName || diagram?.name || fallbackSession.name,
    diagram: diagram ? { ...diagram } : fallbackSession.diagram,
    isDirty: false,
    dirtyFields: new Set<DirtyField>(),
    lastModified: diagram?.lastModified || fallbackSession.lastModified,
    created: created || diagram?.lastModified || fallbackSession.created,
  }
}

export const getDatastructureFieldOptions = (
  version: Pick<DatastructureVersion, 'model' | 'styles' | 'modelName'> | undefined,
): SelectOption[] => {
  const { tree } = versionToSchemaTree(version, version?.modelName ?? '')
  return tree.fields.map(field => ({ value: field.name, label: field.name }))
}
