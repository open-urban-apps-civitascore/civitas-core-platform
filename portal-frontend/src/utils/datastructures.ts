import {
  Datastructure,
  DatastructureCreateFormData,
  DatastructureCreateJsonServerData,
  DatastructuresListData,
} from '@/types/datastructures'

export const mapDatastructuresApiToListData = (datastructures: Datastructure[]): DatastructuresListData[] => {
  return datastructures.map(datastructure => {
    const highestVersion =
      datastructure.versions.length > 0
        ? datastructure.versions.reduce((highest, current) =>
            current.versionNumber.localeCompare(highest.versionNumber, undefined, { numeric: true }) > 0
              ? current
              : highest,
          )
        : null
    return {
      id: datastructure.id,
      name: datastructure.name,
      description: datastructure.description,
      status: datastructure.status,
      versionNumber: highestVersion?.versionNumber || null,
      source: highestVersion?.source || null,
      // add versions field to versions for showing subrows in table
      versions: datastructure.versions.map(version => ({ ...version, versions: [] })),
    }
  })
}

// TODO: This mapper is needed for creating json-server data. remove it when API is implemented
export const mapdatastructureFormToApiData = (
  datastructure: DatastructureCreateFormData,
): DatastructureCreateJsonServerData => ({
  name: datastructure.name,
  description: '',
  source: 'OWN',
  status: 'DRAFT',
  inUse: false,
  versions: [],
})
