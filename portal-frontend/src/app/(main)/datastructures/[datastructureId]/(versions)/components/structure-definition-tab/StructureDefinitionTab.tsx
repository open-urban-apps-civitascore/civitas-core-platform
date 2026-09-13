import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
  isAvailable: boolean
  modelSessionManager: UseMultiSessionReturn
  dataStructureName?: string
  versionName?: string
  datastructureId?: string
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly, isAvailable, modelSessionManager, dataStructureName, versionName, datastructureId } = props

  return (
    <UmlModeler
      isReadOnly={isReadOnly || isAvailable}
      modelSessionManager={modelSessionManager}
      isMultiSessionMode={false}
      canExportModel={false}
      dataStructureName={dataStructureName}
      versionName={versionName}
      datastructureId={datastructureId}
    />
  )
}
