import { PageBackground } from '@/components/page-background/PageBackground'
import { UmlModeler } from '@/components/uml-modeler/page'
import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
  modelSessionManager: UseMultiSessionReturn
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly, modelSessionManager } = props

  return isReadOnly ? (
    <UmlModeler isReadOnly={isReadOnly} modelSessionManager={modelSessionManager} isMultiSessionMode={false} />
  ) : (
    <PageBackground className="overflow-y-auto p-0" hasBackground={!isReadOnly}>
      <UmlModeler isReadOnly={isReadOnly} modelSessionManager={modelSessionManager} isMultiSessionMode={false} />
    </PageBackground>
  )
}
