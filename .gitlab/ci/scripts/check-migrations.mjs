// Checks the Flyway migrations in the working tree, optionally against a target ref.
//
// Usage: node check-migrations.mjs [<target-ref>]
//
// Without a target ref it checks names, duplicate versions and the LATEST file. With a target
// ref (the merge request target) it also checks that migrations the target already has are
// unchanged, and that every new version directly follows the one before it.

import { execFileSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { basename, join } from 'node:path'

const MIGRATIONS = 'portal-backend/src/main/resources/db/migration'
const LATEST_FILE = `${MIGRATIONS}/LATEST`
const NAME_PATTERN = /^V([0-9]+(?:_[0-9]+)*)__[A-Za-z0-9_]+\.sql$/

const git = (cwd, ...args) => {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })
  } catch (error) {
    console.error(`git ${args.join(' ')} failed with exit code ${error.status}`)
    process.exit(error.status ?? 1)
  }
}

const root = git('.', 'rev-parse', '--show-toplevel').trim()
const target = process.argv[2] ?? ''

let errors = 0
const fail = message => {
  console.error(`ERROR: ${message}`)
  errors++
}

const parseVersion = file => NAME_PATTERN.exec(file)[1].split('_').map(Number)
const formatVersion = version => version.join('.')

const compareVersions = (a, b) => {
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    if (a[i] !== b[i]) return a[i] - b[i]
  }
  return a.length - b.length
}

const compareMigrations = (a, b) => compareVersions(a.version, b.version) || a.file.localeCompare(b.file)

const isDirectSuccessor = (previous, next) => {
  if (next.length !== 3) return false
  const [major = 0, minor = 0, patch = 0] = previous
  return [
    [major + 1, 0, 0],
    [major, minor + 1, 0],
    [major, minor, patch + 1],
  ].some(candidate => compareVersions(candidate, next) === 0)
}

const migration = (file, blob) => ({ version: parseVersion(file), key: formatVersion(parseVersion(file)), blob, file })

const byVersion = migrations => Map.groupBy(migrations, m => m.key)

const files = migrations => migrations.map(m => m.file).join(' ')

const migrationDirectoryEntries = () => {
  const directory = join(root, MIGRATIONS)
  if (!existsSync(directory) || !statSync(directory).isDirectory()) return []
  return readdirSync(directory)
    .filter(file => file.startsWith('V'))
    .sort()
}

const branchMigrations = candidates => {
  const names = candidates.filter(file => NAME_PATTERN.test(file))
  if (names.length === 0) return []
  const blobs = git(root, 'hash-object', '--', ...names.map(file => `${MIGRATIONS}/${file}`))
    .trim()
    .split('\n')
  return names.map((file, i) => migration(file, blobs[i])).sort(compareMigrations)
}

const targetMigrations = ref =>
  git(root, 'ls-tree', ref, `${MIGRATIONS}/`)
    .split('\n')
    .filter(Boolean)
    .map(line => {
      const [meta, path] = line.split('\t')
      return { blob: meta.split(' ')[2], file: basename(path) }
    })
    .filter(({ file }) => NAME_PATTERN.test(file))
    .map(({ file, blob }) => migration(file, blob))
    .sort(compareMigrations)

const checkAgainstTarget = (ref, branch, highest) => {
  const targetList = targetMigrations(ref)
  const branchByVersion = byVersion(branch)
  const targetByVersion = byVersion(targetList)

  for (const [key, merged] of targetByVersion) {
    const here = branchByVersion.get(key) ?? []
    for (const m of merged) {
      for (const b of here) {
        if (m.file !== b.file && m.blob === b.blob) {
          fail(`${b.file} renames ${m.file}; a migration that is merged must keep its name`)
        } else if (m.file !== b.file) {
          fail(
            `version ${key}: the target has ${m.file}, this branch has ${b.file}; rebase and take the next free version`,
          )
        } else if (m.blob !== b.blob) {
          fail(`${b.file} differs from the target; a migration that is merged must not change, add a new one`)
        }
      }
    }
    if (here.length === 0 && compareVersions(merged[0].version, highest) <= 0) {
      fail(
        `${files(merged)} is on the target but missing here; a migration that is merged must not be removed or renumbered`,
      )
    }
  }

  let previous = targetList.at(-1)?.version ?? []
  for (const [key, [{ version }]] of branchByVersion) {
    if (targetByVersion.has(key)) continue
    if (!isDirectSuccessor(previous, version)) {
      const after = formatVersion(previous)
      fail(
        `version ${key} does not directly follow ${after}; after ${after} only the next major, minor or patch version is allowed (rebase if the target has moved on)`,
      )
    }
    previous = version
  }
}

const candidates = migrationDirectoryEntries()
for (const file of candidates.filter(file => !NAME_PATTERN.test(file))) {
  fail(`${file}: name a migration V<major>_<minor>_<patch>__<description>.sql`)
}

const branch = branchMigrations(candidates)
if (branch.length === 0) {
  console.error(`No migrations found in ${MIGRATIONS}`)
  process.exit(1)
}

for (const [key, migrations] of byVersion(branch)) {
  if (migrations.length > 1) fail(`version ${key} is used by more than one migration: ${files(migrations)}`)
}

const newest = branch.at(-1)
const latestPath = join(root, LATEST_FILE)
if (!existsSync(latestPath) || !statSync(latestPath).isFile()) {
  fail(`${LATEST_FILE} is missing; it must contain the file name of the newest migration (${newest.file})`)
} else {
  const latest = readFileSync(latestPath, 'utf8').replace(/\s/g, '')
  if (latest !== newest.file) {
    fail(`${LATEST_FILE} must contain the file name of the newest migration ${newest.file}, found '${latest}'`)
  }
}

if (target) checkAgainstTarget(target, branch, newest.version)

if (errors > 0) {
  console.error(`${errors} migration check(s) failed.`)
  process.exit(1)
}
console.log(`Migrations OK (highest version ${newest.key}${target ? `, checked against ${target}` : ''}).`)
