import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
  isAvailable: boolean
  modelSessionManager: UseMultiSessionReturn
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly, isAvailable, modelSessionManager } = props

  return (
    <UmlModeler
      isReadOnly={isReadOnly || isAvailable}
      modelSessionManager={modelSessionManager}
      isMultiSessionMode={false}
      canExportModel={false}
    />
  )
}
