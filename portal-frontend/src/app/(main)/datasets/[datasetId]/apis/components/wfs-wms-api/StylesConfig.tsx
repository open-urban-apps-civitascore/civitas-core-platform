'use client'

import { List, Plus, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useRef, useState } from 'react'
import { FieldPath, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataCard } from '@/components/no-data/no-data-card/NoDataCard'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SidebarList } from '@/components/sidebar-list/SidebarList'
import { Button } from '@/components/ui/button'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { StyleFormData, WfsWmsApiFormData } from '@/types/namedApis'
import { getEmptyLabelIndex, isNewItem } from '@/utils/common'

interface StylesConfigProps {
  form: UseFormReturn<WfsWmsApiFormData>
  existingStyles: StyleFormData[]
  selectedStyleIndex: number | null
  isReadOnly: boolean
  isDeleteStyleLoading?: boolean
  onSelectStyle: (index: number) => void
  onAddStyle: () => void
  onDeleteStyle?: () => Promise<void>
}

export const StylesConfig = (props: StylesConfigProps) => {
  const {
    form,
    existingStyles,
    selectedStyleIndex,
    isReadOnly,
    isDeleteStyleLoading,
    onSelectStyle,
    onAddStyle,
    onDeleteStyle,
  } = props
  const t = useTranslations('datasets.overview.completion.apis.config.styles')
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const stylePath = (path: string): FieldPath<WfsWmsApiFormData> =>
    `styles.${selectedStyleIndex}.${path}` as FieldPath<WfsWmsApiFormData>

  const allStyles = form.watch('styles')

  const wideField = { className: 'grid-cols-[minmax(0,270px)_minmax(0,512px)]' }

  const isSelectedStyleNewAndClean = () => {
    if (selectedStyleIndex === null) return false
    const style = form.getValues(`styles.${selectedStyleIndex}`)
    if (!style?.id.startsWith('new-')) return false
    return !style.name && !style.sldContent
  }

  const handleDeleteClick = () => {
    if (isSelectedStyleNewAndClean()) {
      void onDeleteStyle?.()
    } else {
      setIsDeleteModalOpen(true)
    }
  }

  const handleConfirmDelete = () => {
    void onDeleteStyle?.().then(() => setIsDeleteModalOpen(false))
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || selectedStyleIndex === null) return

    const reader = new FileReader()
    reader.onload = event => {
      const content = event.target?.result as string
      form.setValue(stylePath('sldContent'), content, { shouldDirty: true, shouldValidate: true })
    }
    reader.readAsText(file)

    // Reset file input so the same file can be re-selected
    e.target.value = ''
  }

  const styleErrors = form.formState.errors.styles
  const styleLabels = allStyles.map(style => style.name)
  const sidebarListStyles = allStyles.map((style, index) => {
    const fallbackKey = isNewItem(style) ? 'newStyle' : 'untitledStyle'
    const untitledIndex = !style.name ? getEmptyLabelIndex(styleLabels, index) : 0
    return {
      label: style.name,
      value: style.id,
      displayTitle: style.name || t(fallbackKey, { index: untitledIndex }),
      hasError: !!styleErrors?.[index],
    }
  })
  return (
    <>
      {existingStyles.length === 0 ? (
        <>
          {!isReadOnly && (
            <div className="flex justify-end mb-3">
              <Button type="button" onClick={onAddStyle}>
                <Plus className="h-4 w-4 mr-2" />
                {t('addStyle')}
              </Button>
            </div>
          )}
          <NoDataCard
            icon={<List size={24} />}
            title={t('noStyles.title')}
            subTitle={t('noStyles.subtitle')}
            isDisabled={isReadOnly}
          />
        </>
      ) : (
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-3">
            <SubHeader title={t('title')} titleClassName="text-2xl leading-none font-bold" />
          </DetailsFieldContainer>
          <div className="flex gap-6">
            <SidebarList
              items={sidebarListStyles}
              selectedItemIndex={selectedStyleIndex}
              isReadOnly={isReadOnly}
              addButtonLabel={t('addStyle')}
              addButtonTestId="sidebarAddStyleButton"
              onSelectItem={onSelectStyle}
              onAddItem={onAddStyle}
            />
            {selectedStyleIndex !== null && (
              <div className="flex-1 flex flex-col">
                <DetailsFieldContainer className="border-b-0 py-2 pt-6">
                  <TextField
                    form={form}
                    label={t('name')}
                    name={stylePath('name')}
                    placeholder={t('name')}
                    required
                    disabled={isReadOnly}
                    data-testid="styleNameInput"
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>

                <DetailsFieldContainer className="border-b-0 py-2">
                  <FormItem className={cn('grid', wideField.className)}>
                    <FormLabel>{t('sldFile')}</FormLabel>
                    <div>
                      <Input
                        data-testid="styleSldFileInput"
                        type="text"
                        readOnly
                        disabled={isReadOnly}
                        placeholder={t('sldFilePlaceholder')}
                        className={cn(!isReadOnly && 'cursor-pointer')}
                        onClick={() => {
                          if (isReadOnly) return
                          fileInputRef.current?.click()
                        }}
                      />
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".sld,.xml"
                        className="hidden"
                        disabled={isReadOnly}
                        onChange={handleFileUpload}
                      />
                    </div>
                  </FormItem>
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pb-6">
                  <FormTextArea
                    form={form}
                    label={t('styleEditor')}
                    name={stylePath('sldContent')}
                    placeholder=""
                    className="min-h-[120px] max-h-[300px] overflow-y-auto font-mono text-sm"
                    required
                  />
                </DetailsFieldContainer>

                {!isReadOnly && onDeleteStyle && (
                  <div className="flex justify-end pt-4 pb-2">
                    <Button type="button" variant="outline" size="sm" onClick={handleDeleteClick}>
                      <Trash2 className="h-4 w-4" />
                      {t('deleteStyle')}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        </ContentCard>
      )}

      <WarningModal
        open={isDeleteModalOpen}
        onOpenChange={setIsDeleteModalOpen}
        title={t('deleteModal.title')}
        description={t('deleteModal.description')}
        confirmButtonTitle={t('deleteStyle')}
        onDiscard={() => setIsDeleteModalOpen(false)}
        onConfirm={handleConfirmDelete}
        isLoading={isDeleteStyleLoading}
      />
    </>
  )
}
