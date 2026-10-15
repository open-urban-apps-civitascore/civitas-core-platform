import { Globe, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { get, useFieldArray, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FooterElement } from '@/components/form/FooterElement'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataCard } from '@/components/no-data/no-data-card/NoDataCard'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { InfoBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { MAX_HTTP_API_CALLS } from '@/const/connectors'
import { HttpApiCallForm, HttpApiCallsForm } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

import { ApiCallItem } from './ApiCallItem'

const EMPTY_API_CALL: HttpApiCallForm = { name: '', path: '', queryParameters: [], headers: [], pagination: null }

type ArrayError = { message?: string; root?: { message?: string } }

interface ApiCallsTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  isReadOnly?: boolean
  isDatasourceReleased?: boolean
  dataStructureName?: string
  openIndexes: ReadonlySet<number>
  onOpenIndexesChange: (openIndexes: ReadonlySet<number>) => void
}

export const ApiCallsTab = (props: ApiCallsTabProps) => {
  const {
    form,
    isReadOnly = false,
    isDatasourceReleased = false,
    dataStructureName,
    openIndexes,
    onOpenIndexesChange,
  } = props
  const isDisabled = isReadOnly || isDatasourceReleased
  const tApiCalls = useTranslations('datasources.apiCallsTab')
  const tActions = useTranslations('common.actions')
  const t = useTranslations()

  // DatasourceFormDraft types the configuration as a plain record.
  const apiCallsForm = form as unknown as UseFormReturn<HttpApiCallsForm>
  const { fields, append, remove } = useFieldArray({ control: apiCallsForm.control, name: 'configuration.apiCalls' })

  const [deleteIndex, setDeleteIndex] = useState<number | null>(null)

  const canAdd = !isDisabled && fields.length < MAX_HTTP_API_CALLS
  const arrayError: ArrayError | undefined = get(form.formState.errors, 'configuration.apiCalls')
  const arrayErrorMessage = arrayError?.root?.message ?? arrayError?.message

  const setApiCallOpen = (index: number, isOpen: boolean) => {
    const nextOpenIndexes = new Set(openIndexes)
    if (isOpen) {
      nextOpenIndexes.add(index)
    } else {
      nextOpenIndexes.delete(index)
    }
    onOpenIndexesChange(nextOpenIndexes)
  }

  const handleAdd = () => {
    setApiCallOpen(fields.length, true)
    append(EMPTY_API_CALL, { focusName: `configuration.apiCalls.${fields.length}.path` })
  }

  const handleConfirmDelete = () => {
    if (deleteIndex === null) return
    const remainingIndexes = [...openIndexes].filter(index => index !== deleteIndex)
    const shiftedIndexes = remainingIndexes.map(index => (index > deleteIndex ? index - 1 : index))
    onOpenIndexesChange(new Set(shiftedIndexes))
    remove(deleteIndex)
    setDeleteIndex(null)
  }

  const deleteName =
    deleteIndex === null
      ? ''
      : apiCallsForm.getValues(`configuration.apiCalls.${deleteIndex}.name`) || tApiCalls('unnamed')

  const addButton = canAdd && (
    <Button type="button" onClick={handleAdd}>
      <Plus />
      {tApiCalls('add')}
    </Button>
  )

  return (
    <div className="flex flex-col gap-6">
      {isDatasourceReleased && !isReadOnly && <InfoBox text={tApiCalls('availableInfo')} />}

      {fields.length === 0 ? (
        <NoDataCard
          icon={<Globe />}
          title={tApiCalls('empty.title')}
          buttonText={canAdd ? tApiCalls('add') : undefined}
          onButtonClick={handleAdd}
        />
      ) : (
        <ContentCard className="h-full overflow-auto" footerElement={<FooterElement />}>
          <SubHeader title={tApiCalls('title')} className="pb-4 border-b-1" customElement={addButton} />
          {arrayErrorMessage && <p className="pt-3 text-sm text-destructive">{t(arrayErrorMessage)}</p>}
          <div className="flex flex-col gap-2 pt-3">
            {fields.map((field, index) => (
              <ApiCallItem
                key={field.id}
                form={apiCallsForm}
                index={index}
                isOpen={openIndexes.has(index)}
                isDisabled={isDisabled}
                dataStructureName={dataStructureName}
                onOpenChange={isOpen => setApiCallOpen(index, isOpen)}
                onDelete={() => setDeleteIndex(index)}
              />
            ))}
          </div>
        </ContentCard>
      )}

      <WarningModal
        open={deleteIndex !== null}
        title={tApiCalls('delete.title')}
        description={tApiCalls('delete.description', { name: deleteName })}
        confirmButtonTitle={tActions('delete')}
        onOpenChange={isModalOpen => !isModalOpen && setDeleteIndex(null)}
        onDiscard={() => setDeleteIndex(null)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  )
}
