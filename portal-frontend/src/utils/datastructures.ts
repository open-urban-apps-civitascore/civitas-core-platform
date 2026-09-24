import { versionToSchemaTree } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/schema/versionTree'
import { createEmptySession } from '@/components/uml-modeler/services/sessionService'
import { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { DirtyField } from '@/components/uml-modeler/types/session'
import { SelectOption } from '@/types/common'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
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
  dataStructureVersionIds: datastructure.dataStructureVersions.map(version => version.id),
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
      source: highestVersion?.dataStructureVersionSource || null,
      inUse: datastructure.inUse,
      inUseByReleased: datastructure.inUseByReleased,
      // add versions field to versions for showing subrows in table
      versions: datastructure.dataStructureVersions.map(version => ({
        id: version.id,
        versionNumber: version.version,
        name: version.version ? `Version ${version.version}` : '-',
        description: version.description || '-',
        status: version.dataStructureVersionStatus,
        source: version.dataStructureVersionSource,
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
    source: version.dataStructureVersionSource,
    inUseByReleased: version.inUseByReleased,
  }))

export const mapDatastructureVersionApiToFormData = (version: DatastructureVersion): DatastructureVersionFormData => ({
  id: version.id,
  version: version.version ?? '',
  description: version.description || '',
  dataStructureVersionStatus: version.dataStructureVersionStatus,
  dataStructureVersionSource: version.dataStructureVersionSource,
  modelName: version.modelName,
  nodes: version.styles?.nodes || [],
  edges: version.styles?.edges || [],
})

export const mapDatastructureVersionFormToApiData = (
  version: DatastructureVersionFormData,
  sessionDiagram: UMLDiagram | null,
  model: Record<string, unknown> | null,
): DatastructureVersionPutData => {
  return {
    id: version.id,
    description: version.description,
    dataStructureVersionSource: version.dataStructureVersionSource,
    dataStructureVersionStatus: version.dataStructureVersionStatus,
    modelName: version.modelName,
    model,
    styles: sessionDiagram,
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

export const buildSessionFromVersion = (
  versionData: DatastructureVersion | null,
  sessionId?: string,
  created?: Date,
) => {
  const diagram = versionData?.styles || null
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
