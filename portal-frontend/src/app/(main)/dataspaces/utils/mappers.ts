import { DataSpace, DataSpaceFormData } from '@/types/dataspaces'

export const mapDataspacesToFormData = (dataspace: DataSpace): DataSpaceFormData => {
  return {
    name: dataspace.name,
    description: dataspace.description,
    protected: dataspace.protected,
  }
}
