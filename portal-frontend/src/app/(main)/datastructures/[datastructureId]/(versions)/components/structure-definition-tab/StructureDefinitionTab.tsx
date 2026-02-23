import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly } = props
  return <UmlModeler isUmlModelerReadOnly={isReadOnly} />
}
