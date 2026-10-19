'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { useGetPublishedStructures } from '@/app/services/api/published-structures/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import { DatastructureVersionFormData } from '@/types/datastructures'
import { logicalUrnOf, versionOfUrn } from '@/utils/coreUrn'

/**
 * The published structures the version was built from. The version is what matters — a later
 * version of a structure does not change this one — so it is read off the pinned URN and the
 * whole URN stays available as the title. The URN carries no name, so the name comes from the
 * structures the platform publishes; a structure no longer published shows its logical URN.
 */
const ImportedStructures = ({ urns, label }: { urns: string[]; label: string }) => {
  const sinks = useGetPublishedStructures()
  const nameByLogicalUrn = new Map<string, string>()
  for (const sink of sinks.data?.data ?? []) {
    for (const structure of sink.structures) {
      if (structure.urn) nameByLogicalUrn.set(logicalUrnOf(structure.urn), structure.name)
    }
  }
  return (
    <div className="space-y-1">
      <p className="text-sm font-medium">{label}</p>
      <ul className="text-muted-foreground space-y-0.5 text-sm">
        {urns.map(urn => (
          <li key={urn} title={urn}>
            {nameByLogicalUrn.get(logicalUrnOf(urn)) ?? logicalUrnOf(urn)} {versionOfUrn(urn)}
          </li>
        ))}
      </ul>
    </div>
  )
}

interface VersionInfoTabProps {
  form: UseFormReturn<DatastructureVersionFormData>
  isReadOnly?: boolean
  isAvailable: boolean
  /**
   * The published structures this version was built from, each pinned at the version it was
   * loaded at. Not a form field: the version records them, and nobody types them.
   */
  importedStructureUrns?: string[]
}

export const VersionInfoTab = (props: VersionInfoTabProps) => {
  const { form, isReadOnly = false, isAvailable, importedStructureUrns = [] } = props
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')

  return (
    <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement areAllFieldsRequired />}>
      <Form {...form}>
        <form
          className="max-w-300 flex flex-col"
          data-testid="versionEditForm"
          aria-label={`${tCommon('form')} ${t('versionInfo.title')}`}
          onSubmit={e => e.preventDefault()}
        >
          <DetailsFieldContainer className="pt-0 pb-4 ">
            <SubHeader title={t('versionInfo.title')} />
          </DetailsFieldContainer>

          <DetailsFieldContainer className="max-w-300">
            {/* Assigned by the registry when the model is stored, so it is shown and never typed. */}
            <TextField
              id="version"
              form={form}
              label={t('versionInfo.versionNumber')}
              name="version"
              placeholder={t('versionInfo.versionNumberPlaceholder')}
              disabled
            />
          </DetailsFieldContainer>

          {importedStructureUrns.length > 0 && (
            <DetailsFieldContainer className="max-w-300">
              <ImportedStructures urns={importedStructureUrns} label={t('versionInfo.builtFrom')} />
            </DetailsFieldContainer>
          )}

          <DetailsFieldContainer className="max-w-300">
            <FormTextArea
              form={form}
              name="description"
              label={t('versionInfo.description')}
              placeholder={t('versionInfo.description')}
              hint={tCommon('info.descriptionHint')}
              maxLength={150}
              hasCharacterCount
              disabled={isReadOnly || isAvailable}
              required
              className="min-h-[100px] resize-none"
            />
          </DetailsFieldContainer>

          <DetailsFieldContainer className="max-w-300">
            <TextField
              id="dataStructureVersionSource"
              form={form}
              label={t('versionInfo.source')}
              name="dataStructureVersionSource"
              placeholder={t('source.OWN')}
              disabled={true}
              required
            />
          </DetailsFieldContainer>
        </form>
      </Form>
    </ContentCard>
  )
}
