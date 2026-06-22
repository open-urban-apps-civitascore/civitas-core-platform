import { useTranslations } from 'next-intl'
import { useEffect } from 'react'
import { toast } from 'sonner'

import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

interface StructureDefinitionTabProps {
  isReadOnly: boolean
  isInUse: boolean
  modelSessionManager: UseMultiSessionReturn
}
export const StructureDefinitionTab = (props: StructureDefinitionTabProps) => {
  const { isReadOnly, isInUse, modelSessionManager } = props
  const t = useTranslations('datastructureVersions')

  useEffect(() => {
    if (isInUse && !isReadOnly) toast.info(t('messages.isInUseModelHint'))
  }, [isReadOnly, isInUse, t])

  return (
    <UmlModeler
      isReadOnly={isReadOnly || isInUse}
      modelSessionManager={modelSessionManager}
      isMultiSessionMode={false}
      canExportModel={false}
    />
  )
}
