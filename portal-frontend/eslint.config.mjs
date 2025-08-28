/*
 * ESLint configuration for Civitas Core Platform V2
 * Next.js + TypeScript + Prettier
 *
 * This config is not immutable.
 * If some rules don't make sense or can be improved,
 * we can change them in consultation with the team.
 */

import { dirname } from 'path';
import { fileURLToPath } from 'url';
import { FlatCompat } from '@eslint/eslintrc';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  ...compat.config({
    env: {
      node: true,
      browser: true,
      es2022: true,
    },
    extends: [
      'next/core-web-vitals',
      'plugin:@typescript-eslint/recommended',
      'plugin:prettier/recommended',
    ],
    parser: '@typescript-eslint/parser',
    parserOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      project: './tsconfig.eslint.json',
    },
    plugins: [
      'react',
      '@typescript-eslint',
      'prettier',
      'import',
      'unused-imports',
      'simple-import-sort',
    ],
    rules: {
      // ===================================================
      // NAMING CONVENTIONS
      // ===================================================

      '@typescript-eslint/naming-convention': [
        'error',
        // variables: camelCase, PascalCase for Components or UPPER_CASE for constants
        {
          selector: 'variable',
          format: ['camelCase', 'PascalCase', 'UPPER_CASE'],
          leadingUnderscore: 'allow',
        },
        {
          selector: 'objectLiteralProperty',
          format: null, // no check
          filter: {
            regex: '^--', // Properties starting with "--"
            match: true,
          },
        },
        // functions: camelCase, react components: PascalCase
        {
          selector: 'function',
          format: ['camelCase', 'PascalCase'],
        },
        // parameters: camelCase, "_" allowed for unused parameters
        {
          selector: 'parameter',
          format: ['camelCase'],
          leadingUnderscore: 'allow',
        },
        // Classes, constructors, types: PascalCase
        {
          selector: 'typeLike',
          format: ['PascalCase'],
        },
        // Context: PascalCase
        {
          selector: 'variable',
          modifiers: ['const'],
          format: ['PascalCase'],
          filter: {
            regex: 'Context$', // Alles, was auf "Context" endet
            match: true,
          },
        },
        // Props: camelCase, except when containing React components, then PascalCase
        {
          selector: 'property',
          format: ['camelCase', 'PascalCase'],
        },
        // Booleans (variables) start with prefix is/has/should/can
        {
          selector: 'variable',
          types: ['boolean'],
          format: ['camelCase'],
          leadingUnderscore: 'allow',
          custom: {
            regex: '^(is|has|should|can)[A-Z].*$',
            match: true,
          },
        },

        // Booleans (parameters) start with prefix is/has/should/can
        {
          selector: 'parameter',
          types: ['boolean'],
          format: ['camelCase'],
          custom: {
            regex: '^(is|has|should|can)[A-Z].*$',
            match: true,
          },
        },
      ],

      // ===================================================
      // IMPORTS & EXPORTS
      // ===================================================

      // alphabetically sorted imports
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',

      // remove unused imports
      'unused-imports/no-unused-imports': 'error',

      // warn when unused variables
      'unused-imports/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          varsIgnorePattern: '^_', // Variablen mit "_" werden ignoriert
          args: 'after-used',
          argsIgnorePattern: '^_', // Funktionsargumente mit "_" werden ignoriert
        },
      ],

      // remove conflicting rule
      '@typescript-eslint/no-unused-vars': 'off',

      // no duplicated imports
      'import/no-duplicates': 'error',

      // remove conflicting rules
      'import/order': 'off',
      'sort-imports': 'off',

      // ===================================================
      // REACT
      // ===================================================

      // no index as key: <li key={index}>…</li>
      'react/no-array-index-key': 'warn',
      // no unnecessary <></>
      'react/jsx-no-useless-fragment': 'warn',
      // no unnecessary curly braces: <div>{"Hello"}</div> -> <div>Hello</div>
      'react/jsx-curly-brace-presence': 'warn',
      // boolean props start with prefix
      'react/boolean-prop-naming': [
        'warn',
        {
          rule: '^(is|has|should|can)[A-Z]([A-Za-z0-9]?)',
          validateNested: true,
        },
      ],

      // ===================================================
      // CODE STYLE
      // ===================================================

      // no var → use let/const
      'no-var': 'error',
      'prefer-const': ['error', { destructuring: 'all' }],

      // enforce template strings
      'prefer-template': 'error',

      // Don't include these file extensions in imports
      'import/extensions': [
        'error',
        'ignorePackages',
        {
          '': 'never',
          js: 'never',
          jsx: 'never',
          ts: 'never',
          tsx: 'never',
        },
      ],

      // allow dev dependencies in test and config files
      'import/no-extraneous-dependencies': [
        'error',
        {
          devDependencies: ['**/*.test.ts', '**/*.test.tsx', '**/*.config.js', '**/*.config.ts'],
        },
      ],
    },
  }),
  {
  files: ["**/*.{spec,test}.{ts,tsx}"],
  ...compat.extends("plugin:jest/recommended")[0],
  languageOptions: {
    parserOptions: {
      project: null, // Deaktiviert TS-Projektcheck für Tests
    },
  },
  rules: {
    "@typescript-eslint/naming-convention": "off",
    "import/no-extraneous-dependencies": "off",
    "max-lines": "off",
  },
}

];

export default eslintConfig;
