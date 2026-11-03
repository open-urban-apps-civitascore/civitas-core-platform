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
import {
  ApiCallSection,
  ApiCallSnapshots,
  CollapsedApiCallSections,
  EMPTY_PAGINATION,
  HttpApiCallForm,
  HttpApiCallsForm,
} from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

import { ApiCallItem } from './ApiCallItem'

const EMPTY_API_CALL: HttpApiCallForm = {
  name: '',
  path: '',
  queryParameters: [],
  headers: [],
  pagination: EMPTY_PAGINATION,
}

type ArrayError = { message?: string; root?: { message?: string } }

const API_TOKEN_NAME_PATH = 'configuration.authentication.name'

const NO_COLLAPSED_SECTIONS: ReadonlySet<ApiCallSection> = new Set()

const shiftIndex = (index: number, removedIndex: number) => (index > removedIndex ? index - 1 : index)

const removeEntry = <T,>(entries: ReadonlyMap<number, T>, index: number) => {
  const nextEntries = new Map(entries)
  nextEntries.delete(index)
  return nextEntries
}

const removeEntryAndShift = <T,>(entries: ReadonlyMap<number, T>, removedIndex: number) => {
  const remainingEntries = [...entries].filter(([index]) => index !== removedIndex)
  const shiftedEntries = remainingEntries.map(([index, value]) => [shiftIndex(index, removedIndex), value] as const)
  return new Map(shiftedEntries)
}

interface ApiCallsTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  isReadOnly?: boolean
  isDatasourceReleased?: boolean
  dataStructureName?: string
  editingApiCalls: ApiCallSnapshots
  collapsedSections: CollapsedApiCallSections
  onEditingApiCallsChange: (editingApiCalls: ApiCallSnapshots) => void
  onCollapsedSectionsChange: (collapsedSections: CollapsedApiCallSections) => void
  onApiCallAdd: () => void
  onApiCallRemove: (index: number) => void
}

export const ApiCallsTab = (props: ApiCallsTabProps) => {
  const {
    form,
    isReadOnly = false,
    isDatasourceReleased = false,
    dataStructureName,
    editingApiCalls,
    collapsedSections,
    onEditingApiCallsChange,
    onCollapsedSectionsChange,
    onApiCallAdd,
    onApiCallRemove,
  } = props
  const isDisabled = isReadOnly || isDatasourceReleased
  const tApiCalls = useTranslations('datasources.apiCallsTab')
  const tActions = useTranslations('common.actions')
  const t = useTranslations()

  // DatasourceFormDraft types the configuration as a plain record.
  const apiCallsForm = form as unknown as UseFormReturn<HttpApiCallsForm>
  const { fields, append, remove, update } = useFieldArray({
    control: apiCallsForm.control,
    name: 'configuration.apiCalls',
  })

  const [deleteIndex, setDeleteIndex] = useState<number | null>(null)

  const canAdd = !isDisabled && fields.length < MAX_HTTP_API_CALLS
  const arrayError: ArrayError | undefined = get(form.formState.errors, 'configuration.apiCalls')
  const arrayErrorMessage = arrayError?.root?.message ?? arrayError?.message

  const openEditView = (index: number, snapshot: HttpApiCallForm | null) => {
    onEditingApiCallsChange(new Map(editingApiCalls).set(index, snapshot))
    onCollapsedSectionsChange(removeEntry(collapsedSections, index))
  }

  const removeApiCall = (index: number) => {
    onEditingApiCallsChange(removeEntryAndShift(editingApiCalls, index))
    onCollapsedSectionsChange(removeEntryAndShift(collapsedSections, index))
    onApiCallRemove(index)
    remove(index)
    void apiCallsForm.trigger(API_TOKEN_NAME_PATH)
  }

  // getValues returns the live form objects, which change on input.
  const handleEdit = (index: number) =>
    openEditView(index, structuredClone(apiCallsForm.getValues(`configuration.apiCalls.${index}`)))

  const handleClose = (index: number) => onEditingApiCallsChange(removeEntry(editingApiCalls, index))

  const handleCancel = (index: number) => {
    const snapshot = editingApiCalls.get(index)
    if (!snapshot) {
      removeApiCall(index)
      return
    }
    update(index, snapshot)
    void apiCallsForm.trigger([`configuration.apiCalls.${index}`, API_TOKEN_NAME_PATH])
    handleClose(index)
  }

  const handleCollapsedSectionsChange = (index: number, sections: ReadonlySet<ApiCallSection>) =>
    onCollapsedSectionsChange(new Map(collapsedSections).set(index, sections))

  const handleAdd = () => {
    openEditView(fields.length, null)
    onApiCallAdd()
    append(EMPTY_API_CALL, { focusName: `configuration.apiCalls.${fields.length}.path` })
  }

  const handleConfirmDelete = () => {
    if (deleteIndex === null) return
    removeApiCall(deleteIndex)
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
          <div className="flex flex-col gap-4 pt-3">
            {fields.map((field, index) => (
              <ApiCallItem
                key={field.id}
                form={apiCallsForm}
                index={index}
                isEditing={editingApiCalls.has(index)}
                collapsedSections={collapsedSections.get(index) ?? NO_COLLAPSED_SECTIONS}
                isDisabled={isDisabled}
                dataStructureName={dataStructureName}
                onEdit={() => handleEdit(index)}
                onCollapsedSectionsChange={sections => handleCollapsedSectionsChange(index, sections)}
                onDelete={() => setDeleteIndex(index)}
                onClose={() => handleClose(index)}
                onCancel={() => handleCancel(index)}
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
