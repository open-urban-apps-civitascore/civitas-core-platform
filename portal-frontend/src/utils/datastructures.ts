import { versionToSchemaTree } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/schema/versionTree'
import { diagramFromJsonSchema, SchemaImportError } from '@/components/uml-modeler/services/jsonSchemaImportService'
import { createEmptySession } from '@/components/uml-modeler/services/sessionService'
import { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { DirtyField } from '@/components/uml-modeler/types/session'
import { SelectOption } from '@/types/common'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureFormDraft,
  DatastructuresListData,
  DatastructureVersion,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionPutData,
  DatastructureVersionsListData,
  DatastructureVersionSummary,
} from '@/types/datastructures'

export const mapDatastructureApiToFormData = (datastructure: Datastructure): DatastructureFormDraft => ({
  id: datastructure.id,
  name: datastructure.name ?? '',
  description: datastructure.description ?? '',
  dataStructureStatus: datastructure.dataStructureStatus ?? DATASTRUCTURE_STATUS_TYPES.DRAFT,
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
    return {
      id: datastructure.id,
      dataStructureId: datastructure.id,
      name: datastructure.name,
      description: datastructure.description || '-',
      status: datastructure.dataStructureStatus,
      versionNumber: highestVersion?.version ?? null,
      source: highestVersion ? DATASTRUCTURE_VERSION_SOURCE.OWN : null,
      inUse: datastructure.inUse,
      inUseByReleased: datastructure.inUseByReleased,
      // add versions field to versions for showing subrows in table
      versions: datastructure.dataStructureVersions.map(version => ({
        id: version.id,
        versionNumber: version.version,
        name: version.version ? `Version ${version.version}` : '-',
        description: version.description || '-',
        status: version.dataStructureVersionStatus,
        source: DATASTRUCTURE_VERSION_SOURCE.OWN,
        inUseByReleased: version.inUseByReleased,
        versions: [],
      })),
    }
  })
}

export const mapDatastructureVersionsApiToListData = (
  versions: DatastructureVersionSummary[],
): DatastructureVersionsListData[] =>
  versions.map(version => ({
    id: version.id,
    versionNumber: version.version,
    name: version.version ? `Version ${version.version}` : '-',
    description: version.description || '-',
    status: version.dataStructureVersionStatus,
    source: DATASTRUCTURE_VERSION_SOURCE.OWN,
    inUseByReleased: version.inUseByReleased,
  }))

export const mapDatastructureVersionApiToFormData = (version: DatastructureVersion): DatastructureVersionFormData => ({
  id: version.id,
  version: version.version ?? '',
  description: version.description || '',
  dataStructureVersionStatus: version.dataStructureVersionStatus,
  modelName: version.modelName,
  nodes: version.styles?.nodes || [],
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

type FormFields = DatastructureFormDraft | DatastructureVersionFormData

export const containsNonStatusField = <TData extends FormFields>(fieldsToUpdate: Partial<TData>) =>
  (Object.keys(fieldsToUpdate) as (keyof TData)[]).some(
    key => key !== 'dataStructureStatus' && key !== 'dataStructureVersionStatus',
  )

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
  if (drawn && Array.isArray(drawn.nodes)) return drawn

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
