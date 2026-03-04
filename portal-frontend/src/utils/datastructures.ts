import {
  Datastructure,
  DatastructuresListData,
  DatastructureVersionsListData,
  DatastructureVersionSummary,
} from '@/types/datastructures'

export const mapDatastructuresApiToListData = (datastructures: Datastructure[]): DatastructuresListData[] => {
  return datastructures.map(datastructure => {
    const highestVersion: DatastructureVersionSummary | null =
      datastructure.dataStructureVersions.reduce<DatastructureVersionSummary | null>((highest, current) => {
        if (!highest) return current
        return current.version.localeCompare(highest.version, undefined, { numeric: true }) > 0 ? current : highest
      }, null)
    return {
      id: datastructure.id,
      dataStructureId: datastructure.id,
      name: datastructure.name,
      description: datastructure.description || '-',
      status: datastructure.dataStructureStatus,
      versionNumber: highestVersion?.version || null,
      source: highestVersion?.dataStructureVersionSource || null,
      // add versions field to versions for showing subrows in table
      versions: datastructure.dataStructureVersions.map(version => ({
        id: version.id,
        versionNumber: version.version,
        name: `Version ${version.version}`,
        description: version.description || '-',
        status: version.dataStructureVersionStatus,
        source: version.dataStructureVersionSource,
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
    name: `Version ${version.version}`,
    description: version.description || '-',
    status: version.dataStructureVersionStatus,
    source: version.dataStructureVersionSource,
  }))
