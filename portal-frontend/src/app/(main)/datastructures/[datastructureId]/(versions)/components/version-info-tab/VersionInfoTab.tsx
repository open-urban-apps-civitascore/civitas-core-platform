'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Form } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatastructureVersionFormData } from '@/types/datastructures'

/**
 * The published structures the version was built from. The version is what matters — a later
 * version of a structure does not change this one — so it is read off the pinned URN and the
 * whole URN stays available as the title.
 */
const ImportedStructures = ({ urns, label }: { urns: string[]; label: string }) => (
  <div className="space-y-1">
    <p className="text-sm font-medium">{label}</p>
    <ul className="text-muted-foreground space-y-0.5 text-sm">
      {urns.map(urn => (
        <li key={urn} title={urn}>
          {nameOf(urn)} {versionOf(urn)}
        </li>
      ))}
    </ul>
  </div>
)

/** The name segment of a CORE URN. */
const nameOf = (urn: string): string => urn.split(':')[6] ?? urn

/** The version segment of a CORE URN, or nothing when it carries none. */
const versionOf = (urn: string): string => {
  const segments = urn.split(':')
  return segments.length > 8 ? segments[8] : ''
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
  const isMobile = useIsMobile()

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
            <div className={isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]'}>
              <Label htmlFor="dataStructureVersionSource">
                {t('versionInfo.source')}
                <span className="text-red-500 ml-1">*</span>
              </Label>
              <Input
                id="dataStructureVersionSource"
                data-testid="dataStructureVersionSourceTextField"
                className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
                value={t('source.OWN')}
                disabled
              />
            </div>
          </DetailsFieldContainer>
        </form>
      </Form>
    </ContentCard>
  )
}
