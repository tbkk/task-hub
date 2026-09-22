#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
case "${1:-all}" in
  mini) (cd miniprogram && npm test && npm run typecheck && npm run build:h5 && npm run build:mp-weixin) ;;
  admin) (cd admin-web && npm test && npm run build) ;;
  server) (cd server && mvn test && mvn package -DskipTests) ;;
  all)
    bash "$repo_root/harness/scripts/verify.sh" mini
    bash "$repo_root/harness/scripts/verify.sh" admin
    bash "$repo_root/harness/scripts/verify.sh" server
    git diff --check
    ;;
  *) printf '%s\n' '用法: harness/scripts/verify.sh [all|mini|admin|server]' >&2; exit 2 ;;
esac
