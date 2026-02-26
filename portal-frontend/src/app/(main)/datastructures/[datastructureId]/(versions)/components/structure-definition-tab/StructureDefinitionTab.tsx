import { UmlModeler } from '@/app/(main)/uml-modeler/page'
import { PageBackground } from '@/components/page-background/PageBackground'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly } = props

  return isReadOnly ? (
    <UmlModeler isReadOnly={isReadOnly} />
  ) : (
    <PageBackground className="overflow-y-auto p-0" hasBackground={!isReadOnly}>
      <UmlModeler isReadOnly={isReadOnly} />
    </PageBackground>
  )
}
