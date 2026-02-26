import { UmlModeler } from "@/app/(main)/uml-modeler/page"

interface StructureDefinitionTabProps {
  isReadOnly: boolean
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly } = props
  return <UmlModeler isReadOnly={isReadOnly} />
}
