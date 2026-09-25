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

const root = git('.', 'rev-parse', '--show-toplevel').trim()
let errors = 0

main(process.argv[2] ?? '')

function main(target) {
  const files = migrationFileNames()
  checkFileNames(files)

  const branch = branchMigrations(files)
  if (branch.length === 0) {
    console.error(`No migrations found in ${MIGRATIONS}`)
    process.exit(1)
  }
  const newest = branch.at(-1)

  checkEachVersionIsUsedOnce(branch)
  checkLatestNamesNewestMigration(newest)

  if (target) {
    const merged = targetMigrations(target)
    checkMergedMigrationsAreUnchanged(merged, branch)
    checkNoMergedMigrationIsRemoved(merged, branch, newest)
    checkNewVersionsFollowDirectly(merged, branch)
  }

  if (errors > 0) {
    console.error(`${errors} migration check(s) failed.`)
    process.exit(1)
  }
  console.log(`Migrations OK (highest version ${newest.key}${target ? `, checked against ${target}` : ''}).`)
}

function checkFileNames(files) {
  for (const file of files.filter(file => !NAME_PATTERN.test(file))) {
    fail(`${file}: name a migration V<major>_<minor>_<patch>__<description>.sql`)
  }
}

function checkEachVersionIsUsedOnce(branch) {
  for (const [key, migrations] of groupByVersion(branch)) {
    if (migrations.length > 1) {
      fail(`version ${key} is used by more than one migration: ${fileList(migrations)}`)
    }
  }
}

function checkLatestNamesNewestMigration(newest) {
  const latestPath = join(root, LATEST_FILE)
  if (!isFile(latestPath)) {
    fail(`${LATEST_FILE} is missing; it must contain the file name of the newest migration (${newest.file})`)
    return
  }
  const latest = readFileSync(latestPath, 'utf8').replace(/\s/g, '')
  if (latest !== newest.file) {
    fail(`${LATEST_FILE} must contain the file name of the newest migration ${newest.file}, found '${latest}'`)
  }
}

function checkMergedMigrationsAreUnchanged(merged, branch) {
  const branchByVersion = groupByVersion(branch)
  for (const old of merged) {
    for (const current of branchByVersion.get(old.key) ?? []) {
      const problem = describeChange(old, current)
      if (problem) fail(problem)
    }
  }
}

function describeChange(old, current) {
  const renamed = old.file !== current.file
  const edited = old.blob !== current.blob
  if (renamed && !edited) {
    return `${current.file} renames ${old.file}; a migration that is merged must keep its name`
  }
  if (renamed) {
    return `version ${old.key}: the target has ${old.file}, this branch has ${current.file}; rebase and take the next free version`
  }
  if (edited) {
    return `${current.file} differs from the target; a migration that is merged must not change, add a new one`
  }
  return null
}

// A merged version above the branch's newest one means the branch is behind the target, not that it removed one.
function checkNoMergedMigrationIsRemoved(merged, branch, newest) {
  const branchByVersion = groupByVersion(branch)
  for (const [key, migrations] of groupByVersion(merged)) {
    if (!branchByVersion.has(key) && compareVersions(migrations[0].version, newest.version) <= 0) {
      fail(
        `${fileList(migrations)} is on the target but missing here; a migration that is merged must not be removed or renumbered`,
      )
    }
  }
}

function checkNewVersionsFollowDirectly(merged, branch) {
  const mergedByVersion = groupByVersion(merged)
  const newVersions = [...groupByVersion(branch).values()]
    .map(([migration]) => migration)
    .filter(migration => !mergedByVersion.has(migration.key))

  let previous = merged.at(-1)?.version ?? []
  for (const { key, version } of newVersions) {
    if (!isDirectSuccessor(previous, version)) {
      const after = formatVersion(previous)
      fail(
        `version ${key} does not directly follow ${after}; after ${after} only the next major, minor or patch version is allowed (rebase if the target has moved on)`,
      )
    }
    previous = version
  }
}

function migrationFileNames() {
  const directory = join(root, MIGRATIONS)
  if (!existsSync(directory) || !statSync(directory).isDirectory()) return []
  return readdirSync(directory)
    .filter(file => file.startsWith('V'))
    .sort()
}

function branchMigrations(files) {
  const names = files.filter(file => NAME_PATTERN.test(file))
  if (names.length === 0) return []
  const blobs = git(root, 'hash-object', '--', ...names.map(file => `${MIGRATIONS}/${file}`))
    .trim()
    .split('\n')
  return names.map((file, i) => migration(file, blobs[i])).sort(compareMigrations)
}

function targetMigrations(ref) {
  return git(root, 'ls-tree', ref, `${MIGRATIONS}/`)
    .split('\n')
    .filter(Boolean)
    .map(parseTreeEntry)
    .filter(({ file }) => NAME_PATTERN.test(file))
    .map(({ file, blob }) => migration(file, blob))
    .sort(compareMigrations)
}

// A `git ls-tree` line: "<mode> <type> <blob>\t<path>".
function parseTreeEntry(line) {
  const [meta, path] = line.split('\t')
  return { blob: meta.split(' ')[2], file: basename(path) }
}

function migration(file, blob) {
  const version = NAME_PATTERN.exec(file)[1].split('_').map(Number)
  return { version, key: formatVersion(version), blob, file }
}

function groupByVersion(migrations) {
  return Map.groupBy(migrations, migration => migration.key)
}

function fileList(migrations) {
  return migrations.map(migration => migration.file).join(' ')
}

function formatVersion(version) {
  return version.join('.')
}

function compareVersions(a, b) {
  for (let i = 0; i < Math.min(a.length, b.length); i++) {
    if (a[i] !== b[i]) return a[i] - b[i]
  }
  return a.length - b.length
}

function compareMigrations(a, b) {
  return compareVersions(a.version, b.version) || a.file.localeCompare(b.file)
}

function isDirectSuccessor(previous, next) {
  if (next.length !== 3) return false
  const [major = 0, minor = 0, patch = 0] = previous
  const successors = [
    [major + 1, 0, 0],
    [major, minor + 1, 0],
    [major, minor, patch + 1],
  ]
  return successors.some(successor => compareVersions(successor, next) === 0)
}

function fail(message) {
  console.error(`ERROR: ${message}`)
  errors++
}

function isFile(path) {
  return existsSync(path) && statSync(path).isFile()
}

function git(cwd, ...args) {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })
  } catch (error) {
    console.error(`git ${args.join(' ')} failed with exit code ${error.status}`)
    process.exit(error.status ?? 1)
  }
}
