#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
(cd "$root/server" && mvn test)
(cd "$root/admin-web" && npm test -- --run && npm run build)
(cd "$root/miniprogram" && npm test -- --run && npm run typecheck && npm run build:h5 && npm run build:mp-weixin)
echo "交付验证完成（真实微信、短信、九识和实车仍需外部资源）。"
