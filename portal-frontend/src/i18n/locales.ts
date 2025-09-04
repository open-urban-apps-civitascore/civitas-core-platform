  export const LOCALES = [
    {
      name: "german",
      key: "de",
    },
    {
      name: "english",
      key: "en",
    },
  ] as const;

  export type Locale = typeof LOCALES[number]['key']