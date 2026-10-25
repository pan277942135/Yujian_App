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
TMP_DIR="${RUNNER_TEMP:-/tmp}"
TREE_FILE="$TMP_DIR/swos-design-tree-$$.json"
TREE_REPORT="$TMP_DIR/swos-contract-sha-report-$$.txt"

command -v curl >/dev/null || { echo "ERROR: curl is required."; exit 69; }
command -v python3 >/dev/null || { echo "ERROR: python3 is required."; exit 69; }
command -v sha256sum >/dev/null || { echo "ERROR: sha256sum is required."; exit 69; }
test -f "$MANIFEST" || { echo "ERROR: manifest not found: $MANIFEST"; exit 66; }
python3 -m json.tool "$MANIFEST" >/dev/null

api_get() { curl -fsSL --retry 2 --retry-delay 1 -H "Accept: application/vnd.github+json" "$API/$1"; }
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

echo "PRODUCT_REPOSITORY_CONTROL_PLANE=CROSS_REPO_CANONICAL_BINDING"
echo "PRODUCT_REPOSITORY_CONTROL_PLANE_NOTE=Canonical Work governance is pinned in $APP_REPO; do not create a competing registry in $PRODUCT_REPO"

runs_json="$(api_get "repos/$PRODUCT_REPO/actions/runs?head_sha=$pr_head_live&event=pull_request&per_page=100")"
ci_row="$(printf '%s' "$runs_json" | python3 -c '
import json,sys
d=json.load(sys.stdin)
rs=[r for r in d.get("workflow_runs",[])
    if r.get("head_sha")==sys.argv[1]
    and r.get("head_branch")=="feat/ai-catch-recognition-web-swos-rr09-v1"
    and r.get("head_repository",{}).get("full_name")=="pan277942135/Yujian"
    and r.get("path")==".github/workflows/ci.yml"
    and r.get("status")=="completed" and r.get("conclusion")=="success"]
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

if [[ "$DESIGN_REF" == "main" ]]; then
  design_sha="$app_main_live"
else
  design_ref_json="$(api_get "repos/$APP_REPO/commits/$DESIGN_REF")"
  design_sha="$(printf '%s' "$design_ref_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')"
fi
tree_json="$(api_get "repos/$APP_REPO/git/trees/$design_sha?recursive=1")"
printf '%s' "$tree_json" > "$TREE_FILE"
tree_truncated="$(printf '%s' "$tree_json" | python3 -c 'import json,sys; print(str(json.load(sys.stdin).get("truncated",False)).lower())')"
if [[ "$tree_truncated" == "true" ]]; then
  echo "CONTRACT_SHA=FAIL design tree response is truncated"
  integrity_failures=$((integrity_failures+1))
fi

set +e
python3 - "$MANIFEST" "$TREE_FILE" <<'PY' > "$TREE_REPORT"
import json,sys
manifest=json.load(open(sys.argv[1],encoding="utf-8"))
tree=json.load(open(sys.argv[2],encoding="utf-8"))
entries={x["path"]:x.get("sha") for x in tree.get("tree",[]) if x.get("type")=="blob"}
groups=[
    ("CONTRACT_SHA",manifest["authority"].get("contract_files",[])),
    ("CANONICAL_CONTROL_PLANE",manifest["authority"].get("canonical_control_plane_files",[])),
]
failed=0
for label,items in groups:
    for item in items:
        path=item["path"]
        expected=item["blob_sha"]
        actual=entries.get(path,"MISSING")
        if actual==expected:
            print(f"{label}=PASS {path} {actual}")
        else:
            print(f"{label}=STALE {path} expected={expected} actual={actual}")
            failed+=1
binding=manifest["authority"].get("cross_repository_admission_binding") or {}
if binding.get("canonical_governance_repository")=="pan277942135/Yujian_App" and binding.get("implementation_repository")=="pan277942135/Yujian" and binding.get("local_control_plane_copy_required_in_implementation_repository") is False:
    print("CROSS_REPO_BINDING=PASS single canonical control plane")
else:
    print("CROSS_REPO_BINDING=FAIL missing or conflicting canonical binding")
    failed+=1
sys.exit(1 if failed else 0)
PY
contract_rc=$?
set -e
cat "$TREE_REPORT"
if [[ "$contract_rc" -ne 0 ]]; then integrity_failures=$((integrity_failures+1)); fi

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
if grep -Fq 'TARGET_SHA: ${{ inputs.target_sha }}' <<< "$workflow_text" \
  && grep -Fq 'target_sha:' <<< "$workflow_text" \
  && grep -Fq 'required: true' <<< "$workflow_text" \
  && grep -Fq 'assert pr["head"]["sha"] == expected' <<< "$workflow_text" \
  && grep -Fq 'Latest exact-source necessary CI has not passed' <<< "$workflow_text"; then
  echo "CURRENT_HEAD_PREVIEW_PIN=DYNAMIC_INPUT_REQUIRED exact_live_pr_head=$pr_head_live exact_source_ci_preflight=REQUIRED"
elif [[ "$preview_pin" == "$pr_head_live" ]]; then
  echo "CURRENT_HEAD_PREVIEW_PIN=PASS target_sha=$preview_pin"
else
  echo "CURRENT_HEAD_PREVIEW_PIN=STALE configured_sha=${preview_pin:-UNKNOWN} live_pr_head=$pr_head_live"
fi

bundle_path="${SWOS_PRIVATE_BUNDLE_PATH:-}"
expected_bundle="$(python3 - "$MANIFEST" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))
print((d.get("private_handoff_bundle") or {}).get("sha256",""))
PY
)"
if [[ -z "$bundle_path" ]]; then
  echo "PRIVATE_BUNDLE_SHA=NOT_CHECKED (provide SWOS_PRIVATE_BUNDLE_PATH to verify the private archive)"
elif [[ ! -f "$bundle_path" ]]; then
  echo "PRIVATE_BUNDLE_SHA=INVALID_PATH path=$bundle_path"
  integrity_failures=$((integrity_failures+1))
else
  actual_bundle="$(sha256sum "$bundle_path" | awk '{print $1}')"
  if [[ "$actual_bundle" == "$expected_bundle" ]]; then
    echo "PRIVATE_BUNDLE_SHA=PASS sha=$actual_bundle"
  else
    echo "PRIVATE_BUNDLE_SHA=FAIL expected=$expected_bundle actual=$actual_bundle"
    integrity_failures=$((integrity_failures+1))
  fi
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
    if [[ "$actual" == "$expected" ]]; then
      echo "PRIVATE_ASSET_SHA=PASS asset=$asset_id sha=$actual"
    else
      echo "PRIVATE_ASSET_SHA=FAIL asset=$asset_id expected=$expected actual=$actual"
      integrity_failures=$((integrity_failures+1))
    fi
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
rm -f "$TREE_FILE" "$TREE_REPORT"
