export interface DataSpace {
  id: string
  name: string
  description: string
  protected: boolean
}

export type DataSpaceFormData = Omit<DataSpace, 'id'>
