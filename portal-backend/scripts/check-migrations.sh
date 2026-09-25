#!/usr/bin/env bash
# Checks the Flyway migrations in the working tree, optionally against a target ref.
#
# Usage: check-migrations.sh [<target-ref>]
#
# Without a target ref it checks names, duplicate versions and the LATEST file. With a target
# ref (the merge request target) it also checks that migrations the target already has are
# unchanged, and that every new version directly follows the one before it.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MIGRATIONS=portal-backend/src/main/resources/db/migration
LATEST_FILE=$MIGRATIONS/LATEST
NAME_PATTERN='^V([0-9]+(_[0-9]+)*)__[A-Za-z0-9_]+\.sql$'
target=${1:-}

errors=0
fail() {
  echo "ERROR: $*" >&2
  errors=$((errors + 1))
}

# Each line: "<version> <blob> <file>", sorted by version.
branch_migrations() {
  local path file
  for path in "$MIGRATIONS"/V*; do
    [[ -e $path ]] || continue
    file=${path##*/}
    [[ $file =~ $NAME_PATTERN ]] || continue
    echo "${BASH_REMATCH[1]//_/.} $(git hash-object "$path") $file"
  done | sort -V
}

target_migrations() {
  local blob path file
  git ls-tree "$1" "$MIGRATIONS/" | while read -r _ _ blob path; do
    file=${path##*/}
    [[ $file =~ $NAME_PATTERN ]] || continue
    echo "${BASH_REMATCH[1]//_/.} $blob $file"
  done | sort -V
}

# A successor raises exactly one of major, minor and patch by one and resets the parts after it.
is_successor() {
  local -a prev next
  IFS=. read -ra prev <<<"$1"
  IFS=. read -ra next <<<"$2"
  ((${#next[@]} == 3)) || return 1
  while ((${#prev[@]} < 3)); do prev+=(0); done
  [[ $2 == "$((prev[0] + 1)).0.0" ||
    $2 == "${prev[0]}.$((prev[1] + 1)).0" ||
    $2 == "${prev[0]}.${prev[1]}.$((prev[2] + 1))" ]]
}

for path in "$MIGRATIONS"/V*; do
  [[ -e $path ]] || continue
  file=${path##*/}
  [[ $file =~ $NAME_PATTERN ]] ||
    fail "$file: name a migration V<major>_<minor>_<patch>__<description>.sql"
done

branch=$(branch_migrations)
[[ -n $branch ]] || { echo "No migrations found in $MIGRATIONS" >&2; exit 1; }

for version in $(cut -d' ' -f1 <<<"$branch" | uniq -d); do
  fail "version $version is used by more than one migration: $(awk -v v="$version" '$1 == v {print $3}' <<<"$branch" | xargs)"
done

highest=$(tail -n1 <<<"$branch" | cut -d' ' -f1)
if [[ ! -f $LATEST_FILE ]]; then
  fail "$LATEST_FILE is missing; it must contain the highest migration version ($highest)"
elif [[ $(tr -d '[:space:]' <"$LATEST_FILE") != "$highest" ]]; then
  fail "$LATEST_FILE must contain the highest migration version $highest, found '$(tr -d '[:space:]' <"$LATEST_FILE")'"
fi

if [[ -n $target ]]; then
  target_list=$(target_migrations "$target")
  target_highest=$(tail -n1 <<<"$target_list" | cut -d' ' -f1)

  while read -r version target_blob target_file branch_blob branch_file; do
    if [[ $target_file != "$branch_file" && $target_blob == "$branch_blob" ]]; then
      fail "$branch_file renames $target_file; a migration that is merged must keep its name"
    elif [[ $target_file != "$branch_file" ]]; then
      fail "version $version: the target has $target_file, this branch has $branch_file; rebase and take the next free version"
    elif [[ $target_blob != "$branch_blob" ]]; then
      fail "$branch_file differs from the target; a migration that is merged must not change, add a new one"
    fi
  done < <(join <(sort -k1,1 <<<"$target_list") <(sort -k1,1 <<<"$branch"))

  previous=$target_highest
  for version in $(comm -13 <(cut -d' ' -f1 <<<"$target_list" | sort) <(cut -d' ' -f1 <<<"$branch" | sort) | sort -V); do
    if ! is_successor "$previous" "$version"; then
      fail "version $version does not directly follow $previous; after $previous only the next major, minor or patch version is allowed (rebase if the target has moved on)"
    fi
    previous=$version
  done
fi

if ((errors > 0)); then
  echo "$errors migration check(s) failed." >&2
  exit 1
fi
echo "Migrations OK (highest version $highest${target:+, checked against $target})."
