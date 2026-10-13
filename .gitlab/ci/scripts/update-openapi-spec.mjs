// Brings api/portal-backend/openapi.yaml in line with the spec that export-openapi-spec exported.
//
// Usage: node update-openapi-spec.mjs
//
// Direct pushes to develop are not allowed, so this works the way Renovate does: when the export
// differs from the committed file, it pushes the export to a bot branch and opens a merge request
// against develop, or refreshes the one already open. Reads OPENAPI_BOT_TOKEN and the predefined
// GitLab CI variables from the environment.
//
// The spec is a backend output, so the merge request goes to the reviewers of the backend package
// rule in renovate.json.

import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, readFileSync, statSync } from 'node:fs'

const EXPORT = 'openapi.generated.yaml'
const SPEC_PATH = 'api/portal-backend/openapi.yaml'
const BOT_BRANCH = 'ci/openapi-spec-update'
const TARGET_BRANCH = 'develop'
const RENOVATE_CONFIG = 'renovate.json'
const REVIEWERS_RULE_FILES = 'portal-backend/**'

await main()

async function main() {
  const token = process.env.OPENAPI_BOT_TOKEN
  // Checked before anything else, so a missing token fails the first develop pipeline and not
  // only the next one that changes the API
  if (!token) {
    exitWith('OPENAPI_BOT_TOKEN is not set — cannot push the bot branch or open a merge request.')
  }
  if (!existsSync(EXPORT) || statSync(EXPORT).size === 0) {
    exitWith(`${EXPORT} is missing or empty — export-openapi-spec did not export the spec.`)
  }
  const exported = readFileSync(EXPORT)

  if (existsSync(SPEC_PATH) && readFileSync(SPEC_PATH).equals(exported)) {
    console.log(`${SPEC_PATH} matches the running backend — nothing to do.`)
    return
  }

  const repoUrl = authenticatedRepoUrl(token)
  if (botBranchCarries(repoUrl, exported)) {
    // No push, so a develop pipeline that leaves the API alone adds no commit to the open merge request
    console.log(`Bot branch ${BOT_BRANCH} already carries this spec.`)
  } else {
    pushBotBranch(repoUrl)
  }

  if (await openMergeRequestExists(token)) {
    console.log(`Merge request for ${BOT_BRANCH} is already open — branch updated.`)
    return
  }
  const mergeRequest = await openMergeRequest(token)
  console.log(`Opened merge request ${mergeRequest.web_url}`)
}

function authenticatedRepoUrl(token) {
  const url = new URL(`${env('CI_SERVER_URL')}/${env('CI_PROJECT_PATH')}.git`)
  url.username = 'oauth2'
  url.password = token
  return url.toString()
}

function botBranchCarries(repoUrl, exported) {
  if (git('ls-remote', '--heads', repoUrl, BOT_BRANCH).trim() === '') {
    return false
  }
  git('fetch', '--quiet', '--depth', '1', repoUrl, `refs/heads/${BOT_BRANCH}`)
  const onBranch = gitBytesOrNull('show', `FETCH_HEAD:${SPEC_PATH}`)
  return onBranch !== null && onBranch.equals(exported)
}

function pushBotBranch(repoUrl) {
  copyFileSync(EXPORT, SPEC_PATH)
  git('config', 'user.name', 'CIVITAS CI')
  git('config', 'user.email', `noreply@${env('CI_SERVER_HOST')}`)
  git('checkout', '--quiet', '-B', BOT_BRANCH)
  git('add', SPEC_PATH)
  git(
    'commit',
    '--quiet',
    '-m',
    'chore(api): sync openapi.yaml with the backend on develop',
    '-m',
    `Exported by pipeline ${env('CI_PIPELINE_ID')} from ${env('CI_COMMIT_SHORT_SHA')}.`,
  )
  // Force, because the bot branch is rebuilt from develop on every update and nobody works on it
  git('push', '--quiet', '--force', repoUrl, `HEAD:refs/heads/${BOT_BRANCH}`)
  console.log(`Pushed ${BOT_BRANCH}.`)
}

async function openMergeRequestExists(token) {
  const query = new URLSearchParams({
    state: 'opened',
    source_branch: BOT_BRANCH,
    target_branch: TARGET_BRANCH,
  })
  const open = await gitLabApi(token, `${mergeRequestsUrl()}?${query}`)
  return open.length > 0
}

async function openMergeRequest(token) {
  const reviewerIds = await Promise.all(backendReviewers().map(username => userId(token, username)))
  const body = new URLSearchParams({
    source_branch: BOT_BRANCH,
    target_branch: TARGET_BRANCH,
    title: 'chore(api): sync openapi.yaml with the backend',
    description: [
      `Automated update of \`${SPEC_PATH}\`.`,
      '',
      `The file is the OpenAPI description exported from the backend built in pipeline ` +
        `${env('CI_PIPELINE_URL')} (${TARGET_BRANCH} @ ${env('CI_COMMIT_SHORT_SHA')}) and differs ` +
        'from the committed copy. Do not edit it by hand — merge to bring the repository in line ' +
        `with the running code, or fix the backend and let the next ${TARGET_BRANCH} pipeline ` +
        'refresh this merge request.',
    ].join('\n'),
    remove_source_branch: 'true',
  })
  for (const id of reviewerIds) {
    body.append('reviewer_ids[]', String(id))
  }
  return gitLabApi(token, mergeRequestsUrl(), { method: 'POST', body })
}

function backendReviewers() {
  const { packageRules = [] } = JSON.parse(readFileSync(RENOVATE_CONFIG, 'utf8'))
  const rule = packageRules.find(r => r.matchFileNames?.includes(REVIEWERS_RULE_FILES) && r.reviewers?.length)
  if (!rule) {
    exitWith(`${RENOVATE_CONFIG} has no package rule for ${REVIEWERS_RULE_FILES} with reviewers.`)
  }
  return rule.reviewers
}

async function userId(token, username) {
  const [user] = await gitLabApi(token, `${env('CI_API_V4_URL')}/users?${new URLSearchParams({ username })}`)
  if (!user) {
    exitWith(`GitLab user ${username} from ${RENOVATE_CONFIG} does not exist.`)
  }
  return user.id
}

function mergeRequestsUrl() {
  return `${env('CI_API_V4_URL')}/projects/${env('CI_PROJECT_ID')}/merge_requests`
}

async function gitLabApi(token, url, options = {}) {
  const response = await fetch(url, { ...options, headers: { 'PRIVATE-TOKEN': token } })
  if (!response.ok) {
    exitWith(`${options.method ?? 'GET'} ${url} failed: ${response.status} ${await response.text()}`)
  }
  return response.json()
}

function env(name) {
  const value = process.env[name]
  if (!value) {
    exitWith(`${name} is not set — run this script in a GitLab CI job.`)
  }
  return value
}

function exitWith(message) {
  console.error(message)
  process.exit(1)
}

function git(...args) {
  try {
    return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })
  } catch (error) {
    exitWith(`git ${redact(args).join(' ')} failed with exit code ${error.status}`)
  }
}

function gitBytesOrNull(...args) {
  try {
    return execFileSync('git', args, { stdio: ['ignore', 'pipe', 'ignore'] })
  } catch {
    return null
  }
}

// The repository URL carries the token; keep it out of the error message
function redact(args) {
  return args.map(arg => arg.replace(/\/\/[^@/]*@/, '//***@'))
}
