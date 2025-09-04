"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { LOCALES } from "@/i18n/locales";
import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";

export const LanguageSelect = () => {
  const tLang = useTranslations("languages");
  const router = useRouter();
  const initialLocale = useLocale();
  const handleLanguageChange = (locale: string) => {
    document.cookie = `NEXT_LOCALE=${locale}; path=/; max-age=31536000`; // 1 year valid
    router.refresh(); // Reload with new locale
  };

  return (
    <Select onValueChange={handleLanguageChange} defaultValue={initialLocale}>
      <SelectTrigger className="w-[140px]">
        <SelectValue placeholder="Select Language" />
      </SelectTrigger>
      <SelectContent>
        {LOCALES.map((language) => (
          <SelectItem key={language.key} value={language.key}>
            {tLang(language.name)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};
