#!/usr/bin/env bash
set -euo pipefail

MODE="${1:---report}"
case "$MODE" in --report|--strict) ;; *) echo "Usage: $0 [--report|--strict]" >&2; exit 64;; esac
MANIFEST="${SWOS_INPUT_MANIFEST:-design/pages/recognition/web/implementation_input_pack/SWOS_WEB_IMPLEMENTATION_INPUT_MANIFEST_V1.json}"
DESIGN_REF="${DESIGN_REF:-main}"
APP_REPO="pan277942135/Yujian_App"
PRODUCT_REPO="pan277942135/Yujian"
PR_NUMBER=177
API="https://api.github.com"

command -v curl >/dev/null || { echo "ERROR: curl is required."; exit 69; }
command -v python3 >/dev/null || { echo "ERROR: python3 is required."; exit 69; }
test -f "$MANIFEST" || { echo "ERROR: manifest not found: $MANIFEST"; exit 66; }
python3 -m json.tool "$MANIFEST" >/dev/null

api_get() { curl -fsSL --retry 2 --retry-delay 1 -H "Accept: application/vnd.github+json" "$API/$1"; }
failures=0
source_blockers=0
integrity_failures=0

echo "=== SWOS Web input-pack live audit ==="
echo "manifest=$MANIFEST"
echo "design_ref=$DESIGN_REF"

app_main_json="$(api_get "repos/$APP_REPO/commits/main")"
app_main_live="$(printf '%s' "$app_main_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')"
product_main_json="$(api_get "repos/$PRODUCT_REPO/commits/main")"
product_main_live="$(printf '%s' "$product_main_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')"
pr_json="$(api_get "repos/$PRODUCT_REPO/pulls/$PR_NUMBER")"
pr_head_live="$(printf '%s' "$pr_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["head"]["sha"])')"
pr_branch_live="$(printf '%s' "$pr_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["head"]["ref"])')"
pr_state_live="$(printf '%s' "$pr_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["state"])')"
pr_draft_live="$(printf '%s' "$pr_json" | python3 -c 'import json,sys; print(str(json.load(sys.stdin).get("draft",False)).lower())')"
compare_json="$(api_get "repos/$PRODUCT_REPO/compare/$product_main_live...$pr_head_live")"
ahead_by="$(printf '%s' "$compare_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("ahead_by",0))')"
behind_by="$(printf '%s' "$compare_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("behind_by",0))')"
merge_base="$(printf '%s' "$compare_json" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("merge_base_commit") or {}).get("sha","UNKNOWN"))')"

echo "APP_MAIN_SHA=$app_main_live"
echo "PRODUCT_MAIN_SHA=$product_main_live"
echo "PR177_STATE=$pr_state_live"
echo "PR177_DRAFT=$pr_draft_live"
echo "PR177_BRANCH=$pr_branch_live"
echo "PR177_HEAD_SHA=$pr_head_live"
echo "PR177_AHEAD_BY=$ahead_by"
echo "PR177_BEHIND_BY=$behind_by"
echo "PR177_MERGE_BASE=$merge_base"

if [[ "$pr_state_live" != "open" ]]; then echo "SOURCE_READINESS=BLOCKED_PR_NOT_OPEN"; source_blockers=$((source_blockers+1)); fi
if [[ "$pr_branch_live" != "feat/ai-catch-recognition-web-swos-rr09-v1" ]]; then echo "SOURCE_READINESS=BLOCKED_UNEXPECTED_BRANCH"; source_blockers=$((source_blockers+1)); fi
if [[ "$behind_by" -gt 0 ]]; then echo "SOURCE_READINESS=BLOCKED_BEHIND_MAIN"; source_blockers=$((source_blockers+1)); fi

missing_control_plane=0
for control_path in "AGENTS.md" "CONTROL_PLANE_REGISTRY.yaml"; do
  if api_get "repos/$PRODUCT_REPO/contents/$control_path?ref=$pr_head_live" >/dev/null 2>&1; then
    echo "PRODUCT_CONTROL_PLANE_FILE=PRESENT $control_path"
  else
    echo "PRODUCT_CONTROL_PLANE_FILE=MISSING $control_path"
    missing_control_plane=1
  fi
done
if [[ "$missing_control_plane" -eq 1 ]]; then
  echo "PRODUCT_ADMISSION=BLOCKED_MISSING_REGISTRY"
  source_blockers=$((source_blockers+1))
else
  echo "PRODUCT_ADMISSION=ENTRYPOINT_FILES_PRESENT (canonical Admission must still be executed)"
fi

runs_json="$(api_get "repos/$PRODUCT_REPO/actions/runs?head_sha=$pr_head_live&event=pull_request&per_page=100")"
ci_row="$(printf '%s' "$runs_json" | python3 -c '
import json,sys
d=json.load(sys.stdin)
rs=[r for r in d.get("workflow_runs",[]) if r.get("head_sha")==sys.argv[1] and r.get("path")==".github/workflows/ci.yml" and r.get("status")=="completed" and r.get("conclusion")=="success"]
rs.sort(key=lambda r:r.get("id",0))
if rs: print(str(rs[-1]["id"])+"\t"+rs[-1].get("html_url",""))
' "$pr_head_live")"
if [[ -n "$ci_row" ]]; then
  ci_id="${ci_row%%$'\t'*}"
  ci_url="${ci_row#*$'\t'}"
  echo "EXACT_HEAD_CI=PASS run_id=$ci_id url=$ci_url"
else
  echo "EXACT_HEAD_CI=BLOCKED_NO_SUCCESSFUL_RUN_FOR_CURRENT_HEAD"
  source_blockers=$((source_blockers+1))
fi

# Resolve a branch name or exact commit to the design tree SHA.
if [[ "$DESIGN_REF" == "main" ]]; then
  design_sha="$app_main_live"
else
  design_ref_json="$(api_get "repos/$APP_REPO/commits/$DESIGN_REF")"
  design_sha="$(printf '%s' "$design_ref_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')"
fi
tree_json="$(api_get "repos/$APP_REPO/git/trees/$design_sha?recursive=1")"
tree_truncated="$(printf '%s' "$tree_json" | python3 -c 'import json,sys; print(str(json.load(sys.stdin).get("truncated",False)).lower())')"
if [[ "$tree_truncated" == "true" ]]; then
  echo "CONTRACT_SHA=FAIL design tree response is truncated"
  integrity_failures=$((integrity_failures+1))
fi

echo "=== Verify design contract blob SHAs at $design_sha ==="
while IFS=$'\t' read -r path expected; do
  [[ -n "$path" ]] || continue
  actual="$(printf '%s' "$tree_json" | python3 -c '
import json,sys
d=json.load(sys.stdin); path=sys.argv[1]
row=next((x for x in d.get("tree",[]) if x.get("path")==path and x.get("type")=="blob"),None)
print(row.get("sha","MISSING") if row else "MISSING")
' "$path")"
  if [[ "$actual" == "$expected" ]]; then
    echo "CONTRACT_SHA=PASS $path $actual"
  else
    echo "CONTRACT_SHA=STALE $path expected=$expected actual=$actual"
    integrity_failures=$((integrity_failures+1))
  fi
done < <(python3 - "$MANIFEST" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))
for x in d["authority"]["contract_files"]:
    print(x["path"]+"\t"+x["blob_sha"])
PY
)

echo "=== Verify canonical cross-repository control-plane binding ==="
while IFS=
mapping_text="$(printf '%s' "$mapping_json" | python3 -c 'import base64,json,sys; print(base64.b64decode(json.load(sys.stdin)["content"]).decode("utf-8"))')"
mapping_sha="$(printf '%s' "$mapping_text" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("source_sha",""))')"
authority_head_sha="$(printf '%s' "$mapping_text" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("live_source_audit") or {}).get("live_product_pr_head_sha",""))')"
if [[ "$mapping_sha" == "$pr_head_live" && "$authority_head_sha" == "$pr_head_live" ]]; then
  echo "LIVE_SOURCE_BINDING=PASS source_sha=$mapping_sha"
else
  echo "LIVE_SOURCE_BINDING=STALE mapping_source_sha=$mapping_sha authority_audit_head=$authority_head_sha live_pr_head=$pr_head_live"
  source_blockers=$((source_blockers+1))
fi

workflow_json="$(api_get "repos/$PRODUCT_REPO/contents/.github/workflows/catch-recognition-pr177-no-traffic-deploy.yml?ref=$product_main_live")"
workflow_text="$(printf '%s' "$workflow_json" | python3 -c 'import base64,json,sys; print(base64.b64decode(json.load(sys.stdin)["content"]).decode("utf-8"))')"
preview_pin="$(printf '%s\n' "$workflow_text" | sed -nE 's/^[[:space:]]*TARGET_SHA:[[:space:]]*([0-9a-f]{40}).*/\1/p' | head -n1)"
if [[ "$preview_pin" == "$pr_head_live" ]]; then
  echo "CURRENT_HEAD_PREVIEW_PIN=PASS target_sha=$preview_pin"
else
  echo "CURRENT_HEAD_PREVIEW_PIN=STALE configured_sha=${preview_pin:-UNKNOWN} live_pr_head=$pr_head_live"
fi

for asset_spec in "SWOS_WEB_REFERENCE_PATH:web-three-stage" "SWOS_STORYBOARD_PATH:swos-history" "SWOS_CATCH_PHOTO_PATH:processing-source-photo"; do
  env_name="${asset_spec%%:*}"
  asset_id="${asset_spec#*:}"
  asset_path="${!env_name:-}"
  expected="$(python3 - "$MANIFEST" "$asset_id" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))
for a in d["private_visual_assets"]:
    if a["id"]==sys.argv[2]:
        print(a["sha256"]); break
PY
)"
  if [[ -z "$asset_path" ]]; then
    echo "PRIVATE_ASSET_SHA=NOT_CHECKED asset=$asset_id (provide $env_name to verify bytes)"
  elif [[ ! -f "$asset_path" ]]; then
    echo "PRIVATE_ASSET_SHA=INVALID_PATH asset=$asset_id path=$asset_path"
    integrity_failures=$((integrity_failures+1))
  else
    actual="$(sha256sum "$asset_path" | awk '{print $1}')"
    if [[ "$actual" == "$expected" ]]; then echo "PRIVATE_ASSET_SHA=PASS asset=$asset_id sha=$actual"
    else echo "PRIVATE_ASSET_SHA=FAIL asset=$asset_id expected=$expected actual=$actual"; integrity_failures=$((integrity_failures+1)); fi
  fi
done

echo "PRODUCTION_MASK=NOT_QUALIFIED"
echo "RUNTIME_VISUAL_ACCEPTANCE=NOT_VERIFIED"
if [[ "$integrity_failures" -gt 0 ]]; then
  echo "INPUT_PACK_INTEGRITY=FAIL count=$integrity_failures"
  exit 1
fi
echo "INPUT_PACK_INTEGRITY=PASS"
if [[ "$source_blockers" -gt 0 ]]; then
  echo "LIVE_SOURCE_READINESS=BLOCKED count=$source_blockers"
  if [[ "$MODE" == "--strict" ]]; then exit 2; fi
else
  echo "LIVE_SOURCE_READINESS=PASS"
fi
echo "AUDIT_COMPLETE=YES (source/input readiness only; not a visual-animation pass)"
\t' read -r path expected; do
  [[ -n "$path" ]] || continue
  actual="$(printf '%s' "$tree_json" | python3 -c '
import json,sys
d=json.load(sys.stdin); path=sys.argv[1]
row=next((x for x in d.get("tree",[]) if x.get("path")==path and x.get("type")=="blob"),None)
print(row.get("sha","MISSING") if row else "MISSING")
' "$path")"
  if [[ "$actual" == "$expected" ]]; then
    echo "CANONICAL_CONTROL_PLANE=PASS $path $actual"
  else
    echo "CANONICAL_CONTROL_PLANE=STALE $path expected=$expected actual=$actual"
    integrity_failures=$((integrity_failures+1))
  fi
done < <(python3 - "$MANIFEST" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))
for x in d["authority"].get("canonical_control_plane_files",[]):
    print(x["path"]+"\t"+x["blob_sha"])
PY
)

echo "PRODUCT_REPOSITORY_CONTROL_PLANE=CROSS_REPO_CANONICAL_BINDING"
echo "PRODUCT_REPOSITORY_CONTROL_PLANE_NOTE=Do not create a competing local registry in $PRODUCT_REPO"

mapping_json="$(api_get "repos/$APP_REPO/contents/design/pages/recognition/web/contracts/Work_B_Implementation_Mapping_V2.json?ref=$design_sha")"
mapping_text="$(printf '%s' "$mapping_json" | python3 -c 'import base64,json,sys; print(base64.b64decode(json.load(sys.stdin)["content"]).decode("utf-8"))')"
mapping_sha="$(printf '%s' "$mapping_text" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("source_sha",""))')"
authority_head_sha="$(printf '%s' "$mapping_text" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("live_source_audit") or {}).get("live_product_pr_head_sha",""))')"
if [[ "$mapping_sha" == "$pr_head_live" && "$authority_head_sha" == "$pr_head_live" ]]; then
  echo "LIVE_SOURCE_BINDING=PASS source_sha=$mapping_sha"
else
  echo "LIVE_SOURCE_BINDING=STALE mapping_source_sha=$mapping_sha authority_audit_head=$authority_head_sha live_pr_head=$pr_head_live"
  source_blockers=$((source_blockers+1))
fi

workflow_json="$(api_get "repos/$PRODUCT_REPO/contents/.github/workflows/catch-recognition-pr177-no-traffic-deploy.yml?ref=$product_main_live")"
workflow_text="$(printf '%s' "$workflow_json" | python3 -c 'import base64,json,sys; print(base64.b64decode(json.load(sys.stdin)["content"]).decode("utf-8"))')"
preview_pin="$(printf '%s\n' "$workflow_text" | sed -nE 's/^[[:space:]]*TARGET_SHA:[[:space:]]*([0-9a-f]{40}).*/\1/p' | head -n1)"
if [[ "$preview_pin" == "$pr_head_live" ]]; then
  echo "CURRENT_HEAD_PREVIEW_PIN=PASS target_sha=$preview_pin"
else
  echo "CURRENT_HEAD_PREVIEW_PIN=STALE configured_sha=${preview_pin:-UNKNOWN} live_pr_head=$pr_head_live"
fi

for asset_spec in "SWOS_WEB_REFERENCE_PATH:web-three-stage" "SWOS_STORYBOARD_PATH:swos-history" "SWOS_CATCH_PHOTO_PATH:processing-source-photo"; do
  env_name="${asset_spec%%:*}"
  asset_id="${asset_spec#*:}"
  asset_path="${!env_name:-}"
  expected="$(python3 - "$MANIFEST" "$asset_id" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))
for a in d["private_visual_assets"]:
    if a["id"]==sys.argv[2]:
        print(a["sha256"]); break
PY
)"
  if [[ -z "$asset_path" ]]; then
    echo "PRIVATE_ASSET_SHA=NOT_CHECKED asset=$asset_id (provide $env_name to verify bytes)"
  elif [[ ! -f "$asset_path" ]]; then
    echo "PRIVATE_ASSET_SHA=INVALID_PATH asset=$asset_id path=$asset_path"
    integrity_failures=$((integrity_failures+1))
  else
    actual="$(sha256sum "$asset_path" | awk '{print $1}')"
    if [[ "$actual" == "$expected" ]]; then echo "PRIVATE_ASSET_SHA=PASS asset=$asset_id sha=$actual"
    else echo "PRIVATE_ASSET_SHA=FAIL asset=$asset_id expected=$expected actual=$actual"; integrity_failures=$((integrity_failures+1)); fi
  fi
done

echo "PRODUCTION_MASK=NOT_QUALIFIED"
echo "RUNTIME_VISUAL_ACCEPTANCE=NOT_VERIFIED"
if [[ "$integrity_failures" -gt 0 ]]; then
  echo "INPUT_PACK_INTEGRITY=FAIL count=$integrity_failures"
  exit 1
fi
echo "INPUT_PACK_INTEGRITY=PASS"
if [[ "$source_blockers" -gt 0 ]]; then
  echo "LIVE_SOURCE_READINESS=BLOCKED count=$source_blockers"
  if [[ "$MODE" == "--strict" ]]; then exit 2; fi
else
  echo "LIVE_SOURCE_READINESS=PASS"
fi
echo "AUDIT_COMPLETE=YES (source/input readiness only; not a visual-animation pass)"
