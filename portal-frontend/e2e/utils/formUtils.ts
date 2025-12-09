import { Page } from '@playwright/test'

export const pickSelectOption = async (page: Page, fieldName: string, option: 'Placeholder' | number = 0) => {
  const selectTrigger = page.getByTestId(`${fieldName}SelectTrigger`)
  await selectTrigger.click()

  const selectedOption = page.getByTestId(`${fieldName}SelectItem${option}`)
  await selectedOption.click()
}
