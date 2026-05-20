'use client'

import { List, Plus, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import React, { useCallback, useRef, useState } from 'react'
import { toast } from 'sonner'

import { useCreateStyle, useDeleteStyle, useGetStyles, useUpdateStyle } from '@/app/services/api/styles/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { Stil } from '@/types/styles'

export interface DraftStyle {
  id?: string
  name: string
  sldContent: string
  isNew: boolean
  isDirty: boolean
}

export interface StylesConfigHandle {
  saveAllStyles: () => Promise<boolean>
  hasDirtyStyles: boolean
}

interface StylesConfigProps {
  datasetId: string
}

export const StylesConfig = React.forwardRef<StylesConfigHandle, StylesConfigProps>(({ datasetId }, ref) => {
  const t = useTranslations('datasets.overview.completion.apis.config.styles')
  const isMobile = useIsMobile()

  const { data: stylesResponse } = useGetStyles(datasetId)
  const existingStyles: Stil[] = stylesResponse?.data ?? []

  const createStyle = useCreateStyle()
  const updateStyle = useUpdateStyle()
  const deleteStyle = useDeleteStyle()

  const [draftStyles, setDraftStyles] = useState<DraftStyle[]>([])
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)
  const [deleteTargetIndex, setDeleteTargetIndex] = useState<number | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Merge existing (persisted) styles with local drafts
  const allStyles: DraftStyle[] = [
    ...existingStyles.map(s => {
      const draft = draftStyles.find(d => d.id === s.id)
      if (draft) return draft
      return { id: s.id, name: s.name, sldContent: s.sldContent, isNew: false, isDirty: false }
    }),
    ...draftStyles.filter(d => d.isNew),
  ]

  const selectedStyle = selectedIndex !== null ? allStyles[selectedIndex] : null

  const handleAddStyle = () => {
    const newStyle: DraftStyle = {
      name: '',
      sldContent: '',
      isNew: true,
      isDirty: true,
    }
    setDraftStyles(prev => [...prev, newStyle])
    setSelectedIndex(allStyles.length)
  }

  const updateSelectedStyle = useCallback(
    (field: 'name' | 'sldContent', value: string) => {
      if (selectedIndex === null || !selectedStyle) return

      const updatedStyle: DraftStyle = { ...selectedStyle, [field]: value, isDirty: true }

      setDraftStyles(prev => {
        const existing = prev.findIndex(d =>
          updatedStyle.isNew
            ? d === selectedStyle ||
              (!d.id && d.name === selectedStyle.name && d.sldContent === selectedStyle.sldContent)
            : d.id === updatedStyle.id,
        )
        if (existing >= 0) {
          const next = [...prev]
          next[existing] = updatedStyle
          return next
        }
        return [...prev, updatedStyle]
      })
    },
    [selectedIndex, selectedStyle],
  )

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = event => {
      const content = event.target?.result as string
      updateSelectedStyle('sldContent', content)
    }
    reader.readAsText(file)

    // Reset file input so the same file can be re-selected
    e.target.value = ''
  }

  const handleDeleteClick = (index: number) => {
    setDeleteTargetIndex(index)
  }

  const handleDeleteConfirm = async () => {
    if (deleteTargetIndex === null) return

    const style = allStyles[deleteTargetIndex]

    if (style.id) {
      try {
        await deleteStyle.mutateAsync({ datasetId, stilId: style.id })
        toast.success(t('messages.deleteSuccess'))
      } catch {
        toast.error(t('messages.deleteError'))
        setDeleteTargetIndex(null)
        return
      }
    } else {
      toast.success(t('messages.deleteSuccess'))
    }

    // Remove from draft styles
    setDraftStyles(prev => prev.filter(d => (style.isNew ? d !== style : d.id !== style.id)))

    // Adjust selection
    if (selectedIndex === deleteTargetIndex) {
      setSelectedIndex(null)
    } else if (selectedIndex !== null && selectedIndex > deleteTargetIndex) {
      setSelectedIndex(selectedIndex - 1)
    }

    setDeleteTargetIndex(null)
  }

  const handleDeleteCancel = () => {
    setDeleteTargetIndex(null)
  }

  // Save all dirty styles — called from parent via ref or imperative handle
  const saveAllStyles = async (): Promise<boolean> => {
    let allSaved = true

    for (const style of draftStyles) {
      if (!style.isDirty) continue
      if (!style.name.trim() || !style.sldContent.trim()) continue

      try {
        if (style.isNew) {
          await createStyle.mutateAsync({
            datasetId,
            style: { name: style.name.trim(), sldContent: style.sldContent },
          })
        } else if (style.id) {
          await updateStyle.mutateAsync({
            datasetId,
            stilId: style.id,
            style: { name: style.name.trim(), sldContent: style.sldContent },
          })
        }
      } catch {
        allSaved = false
        toast.error(t('messages.saveError'))
      }
    }

    if (allSaved) {
      setDraftStyles([])
    }

    return allSaved
  }

  const hasDirtyStyles = draftStyles.some(d => d.isDirty)

  React.useImperativeHandle(ref, () => ({
    saveAllStyles,
    hasDirtyStyles,
  }))

  // Empty state
  if (allStyles.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex justify-end">
          <Button type="button" onClick={handleAddStyle}>
            <Plus className="mr-2 h-4 w-4" />
            {t('addStyle')}
          </Button>
        </div>
        <div className="flex flex-col items-center gap-6 p-6 rounded-lg border border-dashed border-border bg-white">
          <div className="flex w-12 h-12 p-2 justify-center items-center gap-2 rounded-md border border-border bg-white shadow-xs">
            <List size={24} />
          </div>
          <NoDataPage
            title={t('noStyles.title')}
            subTitle={t('noStyles.subtitle')}
            className="border-0 shadow-none p-0 items-center text-center"
          />
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col">
      <div className="flex justify-end mb-4">
        <Button type="button" onClick={handleAddStyle}>
          <Plus className="mr-2 h-4 w-4" />
          {t('addStyle')}
        </Button>
      </div>

      <ContentCard className={cn('flex gap-6 h-auto', isMobile ? 'flex-col' : 'flex-row')}>
        {/* Left sidebar — style list */}
        <div className={cn('flex flex-col gap-2', isMobile ? 'w-full' : 'w-[240px] shrink-0')}>
          {allStyles.map((style, index) => (
            <button
              key={style.id ?? `new-${index}`}
              type="button"
              onClick={() => setSelectedIndex(index)}
              className={cn(
                'text-left px-4 py-2 rounded-sm border text-sm transition-colors',
                selectedIndex === index
                  ? 'bg-primary/10 border-primary font-medium'
                  : 'bg-white border-border hover:bg-muted',
              )}
            >
              {style.name || `Style ${index + 1}`}
            </button>
          ))}
        </div>

        {/* Right panel — style detail */}
        {selectedStyle && (
          <div className="flex-1 flex flex-col">

            {/* Name field */}
            <DetailsFieldContainer className="border-b-0 py-2 pt-6">
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium">{t('name')}</label>
                <Input
                  data-testid="styleNameInput"
                  value={selectedStyle.name}
                  onChange={e => updateSelectedStyle('name', e.target.value)}
                  placeholder=""
                />
              </div>
            </DetailsFieldContainer>

            {/* SLD file upload */}
            <DetailsFieldContainer className="border-b-0 py-2">
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium">{t('sldFile')}</label>
                <div>
                  <Input
                    data-testid="styleSldFileInput"
                    type="text"
                    readOnly
                    placeholder={t('sldFilePlaceholder')}
                    className="cursor-pointer"
                    onClick={() => fileInputRef.current?.click()}
                  />
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".sld,.xml"
                    className="hidden"
                    onChange={handleFileUpload}
                  />
                </div>
              </div>
            </DetailsFieldContainer>

            {/* Style editor */}
            <DetailsFieldContainer className="border-b-0 py-2 pb-6">
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium">{t('styleEditor')}</label>
                <Textarea
                  data-testid="styleEditorTextArea"
                  className="min-h-[120px] max-h-[300px] overflow-y-auto font-mono text-sm"
                  value={selectedStyle.sldContent}
                  onChange={e => updateSelectedStyle('sldContent', e.target.value)}
                />
              </div>
            </DetailsFieldContainer>

            {/* Delete button */}
            <div className="border-t pt-4 flex justify-end">
              <Button type="button" variant="outline" onClick={() => handleDeleteClick(selectedIndex!)}>
                <Trash2 className="mr-2 h-4 w-4" />
                {t('deleteStyle')}
              </Button>
            </div>
          </div>
        )}
      </ContentCard>

      {/* Delete confirmation modal */}
      <WarningModal
        title={t('deleteModal.title')}
        description={t('deleteModal.description')}
        open={deleteTargetIndex !== null}
        onOpenChange={open => {
          if (!open) handleDeleteCancel()
        }}
        onDiscard={handleDeleteCancel}
        onConfirm={handleDeleteConfirm}
        confirmButtonTitle={t('deleteStyle')}
        isLoading={deleteStyle.isPending}
      />
    </div>
  )
})

StylesConfig.displayName = 'StylesConfig'
