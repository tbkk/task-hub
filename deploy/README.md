# 部署配置

本地联调部署：Docker Compose、MySQL 8.4、Java 17、Nginx。生产域名、TLS、备份和真实鉴权在上线前另行配置。

## 启动

在项目根目录执行（需 Docker Engine／Docker Desktop 和 Compose v2）：

```sh
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，为应用用户和 root 设置不同密码
docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yaml up --build -d
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
```

打开 `http://localhost:8088`。`/api/` 由 Nginx 保留前缀转发给后端；数据库和 Java 端口默认不暴露到宿主机。入口仅绑定本机，当前配置用于本地联调。

```sh
curl -f http://localhost:8088/api/health
curl -f http://localhost:8088/api/ready
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 server
```

`health` 仅确认 Java 进程存活；`ready` 确认 MyBatis 能读取数据库元数据。Compose 按 MySQL → 后端就绪 → 管理端依次启动；业务接口默认拒绝访问。

## 数据与停止

```sh
docker compose --env-file deploy/.env -f deploy/compose.yaml down
```

停止不会删除命名数据卷。`database/init/` 仅在空数据目录初始化时运行；已有数据库需显式执行后续迁移。不要使用 `down -v` 清理有业务数据的环境。

`.env` 不提交，前端 `VITE_*` 变量会打包进客户端，不能存放微信、短信、九识凭证。小程序不是 Nginx 部署产物，应由微信开发者工具上传，并使用已配置合法域名的 HTTPS 后端地址。

## 验证边界

本轮执行环境未安装 Docker，容器构建、Compose 启动及真实 MySQL 联调需在具备 Docker 的环境按上述命令验证。源码构建及后端测试结果见根 README。
