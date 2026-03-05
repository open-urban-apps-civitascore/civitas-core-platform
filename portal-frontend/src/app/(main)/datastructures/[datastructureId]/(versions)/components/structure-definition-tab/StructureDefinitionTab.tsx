import { PageBackground } from '@/components/page-background/PageBackground'
import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'
import { toast } from 'sonner'

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

  return isReadOnly || isInUse ? (
    <UmlModeler
      isReadOnly={isReadOnly || isInUse}
      modelSessionManager={modelSessionManager}
      isMultiSessionMode={false}
    />
  ) : (
    <PageBackground className="overflow-y-auto p-0" hasBackground={!isReadOnly && !isInUse}>
      <UmlModeler
        isReadOnly={isReadOnly || isInUse}
        modelSessionManager={modelSessionManager}
        isMultiSessionMode={false}
        canExportXmi={false}
        canImportXmi={false}
      />
    </PageBackground>
  )
}
