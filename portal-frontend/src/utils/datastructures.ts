import { Datastructure, DatastructuresListData } from '@/types/datastructures'

export const mapDatastructuresApiToListData = (datastructures: Datastructure[]): DatastructuresListData[] => {
  return datastructures.map(datastructure => {
    const highestVersion = datastructure.versions.reduce((highest, current) =>
      current.versionNumber.localeCompare(highest.versionNumber, undefined, { numeric: true }) > 0 ? current : highest,
    )
    return {
      id: datastructure.id,
      name: datastructure.name,
      description: datastructure.description,
      status: datastructure.status,
      versionNumber: highestVersion.versionNumber,
      source: highestVersion.source,
      // add versions field to versions for showing subrows in table
      versions: datastructure.versions.map(version => ({
        ...version,
        versions: [],
        name: `Version ${version.versionNumber}`,
      })),
    }
  })
}
