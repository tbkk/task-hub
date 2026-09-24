#!/usr/bin/env bash
# Task Hub 本机实验部署；不管理 MySQL，不使用全局 Nginx 配置。
set -euo pipefail
umask 077
repo="$(cd "$(dirname "$0")" && pwd -P)"
info() { printf '[ok] %s\n' "$*"; }
fail() { printf '[错误] %s\n' "$*" >&2; exit 1; }
usage() { printf '用法: %s [--force] {deploy|restart|stop|status|logs|help}\n' "$0"; }
force_deploy=false
if [[ "${1:-}" == --force ]]; then force_deploy=true; shift; fi
action="${1:-deploy}"
if "$force_deploy" && [[ "$action" != deploy ]]; then usage >&2; exit 2; fi
case "$action" in deploy|restart|stop|status|logs) ;; help|--help) usage; exit 0;; *) usage >&2; exit 2;; esac
[[ $# -le 1 ]] || { usage >&2; exit 2; }

# 配置只解析字面量，不 source、不 eval；外部环境优先于文件。
env_file="${TASKHUB_DEPLOY_ENV_FILE:-$repo/.env.local}"
if [[ -f "$env_file" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" == *=* ]] || fail '配置必须为 KEY=VALUE'
    key="${line%%=*}"; value="${line#*=}"
    case "$key" in
      TASKHUB_DEPLOY_RUNTIME|TASKHUB_DEPLOY_ADMIN_PORT|TASKHUB_DEPLOY_H5_PORT|TASKHUB_DEPLOY_SERVER_PORT|TASKHUB_DEPLOY_NGINX_BIN|TASKHUB_DEPLOY_MIME_TYPES|MYSQL_HOST|MYSQL_PORT|MYSQL_DATABASE|MYSQL_USER|MYSQL_PASSWORD|TASKHUB_AUTH_MOCK_WECHAT_CODE|TASKHUB_AUTH_MOCK_WECHAT_OPENID) ;;
      *) fail '配置项不在允许列表中';;
    esac
    if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
    if ! printenv "$key" >/dev/null 2>&1; then export "$key=$value"; fi
  done < "$env_file"
fi

runtime="${TASKHUB_DEPLOY_RUNTIME:-/tmp/task-hub-local-deploy}"
[[ "$runtime" =~ ^/[a-zA-Z0-9_./-]+$ ]] || fail '运行目录必须为不含空格的绝对路径'
command -v python3 >/dev/null || fail '缺少 python3'
runtime="$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$runtime")"
[[ "$runtime" != / && "$runtime" != /tmp && "$runtime" != /private/tmp && "$runtime" != "$HOME" && "$runtime" != "$repo" && ${#runtime} -ge 16 ]] || fail '拒绝过宽运行目录'
[[ "$runtime" == /private/tmp/* || "$runtime" == /tmp/* || "$runtime" == "$HOME/"* ]] || fail '运行目录必须位于用户目录或临时目录内'
admin_port="${TASKHUB_DEPLOY_ADMIN_PORT:-18090}"
h5_port="${TASKHUB_DEPLOY_H5_PORT:-18091}"
server_port="${TASKHUB_DEPLOY_SERVER_PORT:-18092}"
for port in "$admin_port" "$h5_port" "$server_port"; do
  [[ "$port" =~ ^[1-9][0-9]{3,4}$ ]] && (( port <= 65535 )) || fail '端口必须为 1024–65535'
  (( port >= 1024 )) || fail '端口必须为 1024–65535'
done
[[ "$admin_port" != "$h5_port" && "$admin_port" != "$server_port" && "$h5_port" != "$server_port" ]] || fail '端口不可重复'
nginx_bin="${TASKHUB_DEPLOY_NGINX_BIN:-$(command -v nginx || true)}"
nginx_conf="$runtime/nginx/nginx.conf"
locked=false
starting=false
mkdir -p "$runtime/pids" "$runtime/logs" "$runtime/nginx" "$runtime/releases"

pid_file() { if [[ "$1" == backend ]]; then printf '%s/pids/backend.pid' "$runtime"; else printf '%s/nginx/nginx.pid' "$runtime"; fi; }
alive() { [[ "$1" =~ ^[0-9]+$ ]] && (( $1 > 1 )) && kill -0 "$1" 2>/dev/null; }
owned() {
  local kind="$1" pid="$2" cmd
  alive "$pid" || return 1
  cmd="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  cmd="${cmd%"${cmd##*[![:space:]]}"}"
  if [[ "$kind" == backend ]]; then
    [[ "$cmd" == *" -Dtaskhub.local.runtime=$runtime -jar $runtime/releases/"*"/server.jar" ]]
  else
    [[ "$cmd" == "nginx: master process "*" -p $runtime/nginx/ -c $nginx_conf" ]]
  fi
}
force_stop_backend() {
  local pid="$1" count
  alive "$pid" || return 0
  printf '[警告] 强制处理后端进程 PID %s：先 TERM，15 秒未退出则 KILL\n' "$pid" >&2
  kill -TERM "$pid" || { alive "$pid" && return 1; return 0; }
  for ((count=0; count<15; count++)); do
    alive "$pid" || return 0
    sleep 1
  done
  kill -KILL "$pid" || { alive "$pid" && return 1; return 0; }
  for ((count=0; count<15; count++)); do
    alive "$pid" || return 0
    sleep 1
  done
  fail "无法终止后端进程 PID $pid"
}
force_stop_port_occupants() {
  local pid
  "$force_deploy" || return 0
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    printf '[警告] --force 将终止后端端口 %s 的监听者 PID %s\n' "$server_port" "$pid" >&2
    force_stop_backend "$pid"
  done < <(lsof -nP -tiTCP:"$server_port" -sTCP:LISTEN 2>/dev/null || true)
}
stop_one() {
  local kind="$1" file pid count
  file="$(pid_file "$kind")"
  [[ -f "$file" ]] || return 0
  read -r pid < "$file" || true
  if ! alive "${pid:-}"; then rm -f "$file"; return 0; fi
  if [[ "$kind" == backend ]] && "$force_deploy"; then
    if ! owned backend "$pid"; then printf '[警告] 后端 PID 归属不符，按 --force 终止 PID %s\n' "$pid" >&2; fi
    force_stop_backend "$pid"
    rm -f "$file"
    return 0
  fi
  owned "$kind" "$pid" || { printf '[错误] %s PID 归属不符，未停止\n' "$kind" >&2; return 1; }
  if [[ "$kind" == nginx ]]; then kill -QUIT "$pid"; else kill -TERM "$pid"; fi
  for ((count=0; count<15; count++)); do
    if ! alive "$pid"; then rm -f "$file"; return 0; fi
    sleep 1
  done
  printf '[错误] %s 未在 15 秒内停止\n' "$kind" >&2
  return 1
}
cleanup() {
  local rc=$?
  trap - EXIT
  if "$starting" && (( rc != 0 )); then stop_one nginx || true; stop_one backend || true; fi
  if "$locked"; then rm -f "$runtime/lock/pid"; rmdir "$runtime/lock"; fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
lock() {
  mkdir "$runtime/lock" 2>/dev/null || fail "部署锁已存在：$runtime/lock；确认没有命令运行后再删除残留锁"
  locked=true
  printf '%s\n' "$$" > "$runtime/lock/pid"
}
require() { command -v "$1" >/dev/null || fail "缺少依赖：$1"; }
port_free_or_owned() {
  local port="$1" kind="$2" pid parent recorded
  recorded="$(cat "$(pid_file "$kind")" 2>/dev/null || true)"
  while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    if [[ "$pid" == "$recorded" ]] && owned "$kind" "$pid"; then continue; fi
    # Nginx worker 继承监听描述符，归属由其 master 再次确认。
    parent="$(ps -p "$pid" -o ppid= | tr -d ' ')"
    if [[ "$kind" == nginx && "$parent" == "$recorded" ]] && owned nginx "$parent"; then continue; fi
    fail "端口 $port 被其他进程占用（PID ${pid}），请换端口"
  done < <(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
}
preflight() {
  local dep
  for dep in java curl lsof ps; do require "$dep"; done
  [[ -x "$nginx_bin" ]] || fail '未找到 Nginx，请安装或配置 TASKHUB_DEPLOY_NGINX_BIN'
  [[ -n "${MYSQL_DATABASE:-}" && -n "${MYSQL_USER:-}" ]] || fail '请在 .env.local 配置 MYSQL_DATABASE 和 MYSQL_USER'
  [[ -n "${TASKHUB_AUTH_MOCK_WECHAT_CODE:-}" && -n "${TASKHUB_AUTH_MOCK_WECHAT_OPENID:-}" ]] || fail '请配置本地模拟微信身份'
  port_free_or_owned "$admin_port" nginx
  port_free_or_owned "$h5_port" nginx
  # 强制终止延后至构建及 Nginx 预检成功后，构建失败不影响旧进程。
  if ! "$force_deploy"; then port_free_or_owned "$server_port" backend; fi
}
render() {
  local mime="${TASKHUB_DEPLOY_MIME_TYPES:-/opt/homebrew/etc/nginx/mime.types}"
  [[ -f "$mime" ]] || fail '请配置 TASKHUB_DEPLOY_MIME_TYPES'
  python3 - "$repo/deploy/local.nginx.conf" "$nginx_conf.next" "$runtime" "$mime" "$admin_port" "$h5_port" "$server_port" <<'PY'
from pathlib import Path
import sys
source, dest, runtime, mime, admin, h5, backend = sys.argv[1:]
assert not any(c in mime for c in '\n\r"$\\'), 'mime.types 路径含不支持字符'
text = Path(source).read_text()
for key, value in dict(RUNTIME=runtime, MIME=mime, ADMIN=admin, H5=h5, BACKEND=backend).items():
    text = text.replace('__'+key+'__', value)
Path(dest).write_text(text)
PY
  "$nginx_bin" -t -p "$runtime/nginx/" -c "$nginx_conf.next"
}
wait_url() {
  local url="$1" count
  for ((count=0; count<45; count++)); do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  return 1
}
verify() {
  local port code kind pid
  for kind in backend nginx; do
    pid="$(cat "$(pid_file "$kind")" 2>/dev/null || true)"
    owned "$kind" "$pid" || return 1
  done
  for port in "$admin_port" "$h5_port"; do
    curl -fsS --max-time 5 "http://127.0.0.1:$port/" >/dev/null || return 1
    curl -fsS --max-time 5 "http://127.0.0.1:$port/api/ready" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"] == 0' || return 1
    code="$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/identity/me")"
    [[ "$code" == 401 ]] || return 1
  done
}
start_release() {
  local release="$1"
  [[ -f "$release/server.jar" && -f "$release/admin/index.html" && -f "$release/h5/index.html" ]] || fail '发布产物不完整，请执行 deploy'
  render
  stop_one nginx
  stop_one backend
  force_stop_port_occupants
  starting=true
  (
    cd "$repo/server"
    export SPRING_PROFILES_ACTIVE=local TASKHUB_AUTH_MOCK_ENABLED=true SIMULATOR_ENABLED=true NOTIFICATIONS_ENABLED=true
    export TASKHUB_MIGRATIONS_ENABLED=true SERVER_PORT="$server_port" SERVER_ADDRESS=127.0.0.1
    python3 - "$runtime" "$release" <<'PY'
import subprocess,sys
from pathlib import Path
runtime, release = sys.argv[1:]
with open(runtime+'/logs/backend.log', 'ab') as log:
    process = subprocess.Popen(['java', '-Dtaskhub.local.runtime='+runtime, '-jar', release+'/server.jar'],
                               stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
Path(runtime+'/pids/backend.pid').write_text(str(process.pid)+'\n')
PY
  )
  wait_url "http://127.0.0.1:$server_port/api/ready" || fail "后端未就绪，查看 $runtime/logs/backend.log"
  owned backend "$(cat "$runtime/pids/backend.pid")" || fail '新后端进程已退出，拒绝使用其他实例的健康结果'
  python3 - "$runtime" "$release" <<'PY'
import os,sys
root, release = sys.argv[1:]
temp = root + '/current.next'
if os.path.lexists(temp): os.unlink(temp)
os.symlink(release, temp)
os.replace(temp, root + '/current')
PY
  mv "$nginx_conf.next" "$nginx_conf"
  "$nginx_bin" -p "$runtime/nginx/" -c "$nginx_conf"
  verify || fail '部署探测失败，检查运行日志'
  starting=false
  info "部署就绪：管理端 http://127.0.0.1:$admin_port/ 小程序H5 http://127.0.0.1:$h5_port/"
}
build_release() {
  require npm; require mvn; require node
  node -e 'const [a,b]=process.versions.node.split(".").map(Number); if(a<22 || (a===22 && b<12)) process.exit(1)' || fail '构建需要 Node.js 22.12 或更高版本'
  [[ -d "$repo/admin-web/node_modules" && -d "$repo/miniprogram/node_modules" ]] || fail '请先在 admin-web 和 miniprogram 中执行 npm ci'
  release="$(mktemp -d "$runtime/releases/$(date +%Y%m%d-%H%M%S)-XXXXXX")"
  (cd "$repo/admin-web"; npm test; VITE_API_BASE_URL=/api npm run build)
  (cd "$repo/miniprogram"; npm test; npm run typecheck; VITE_API_BASE_URL=/api VITE_AUTH_MOCK=false npm run build:h5)
  # 测试显式移除部署配置；业务 IT 须使用独立专用库另行执行。
  (cd "$repo/server"; env -u SPRING_PROFILES_ACTIVE -u TASKHUB_MIGRATIONS_ENABLED -u MYSQL_DATABASE -u MYSQL_USER -u MYSQL_PASSWORD mvn package)
  mkdir "$release/admin" "$release/h5"
  cp -R "$repo/admin-web/dist/." "$release/admin/"
  cp -R "$repo/miniprogram/dist/build/h5/." "$release/h5/"
  cp "$repo/server/target/task-hub-server-0.0.1-SNAPSHOT.jar" "$release/server.jar"
}
case "$action" in
  deploy) lock; preflight; build_release; start_release "$release";;
  restart) lock; preflight; [[ -L "$runtime/current" ]] || fail '尚无发布，请执行 deploy'; start_release "$(readlink "$runtime/current")";;
  stop) lock; stop_one nginx; stop_one backend; info '托管服务已停止，MySQL保持运行';;
  status)
    result=0
    for kind in backend nginx; do
      file="$(pid_file "$kind")"; pid="$(cat "$file" 2>/dev/null || true)"
      if owned "$kind" "$pid"; then info "$kind 运行中（PID ${pid}）"; else printf '%s 未运行或归属不符\n' "$kind"; result=1; fi
    done
    if (( result == 0 )); then verify || result=1; fi
    exit "$result";;
  logs)
    for file in "$runtime/logs/backend.log" "$runtime/nginx/error.log"; do
      printf '日志：%s\n' "$file"
      [[ ! -f "$file" ]] || tail -40 "$file"
    done;;
esac
