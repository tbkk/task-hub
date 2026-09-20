# 数据库脚本

数据库：MySQL 8.4，字符集 `utf8mb4`。

- `init/001_app_metadata.sql`：工程元数据表及版本标记，供后端就绪检查查询。
- 业务表（账号、授权、订单、批次、任务、工单等）随对应模块实施，当前不固化未确认的业务模型。

Compose 首次创建数据卷时自动执行 `init/` 中的 SQL；已有数据卷不会重新运行初始化脚本。SQL 不创建业务用户或授予权限，数据库名与应用用户由 Compose 环境变量或 DBA 创建。

本地已有 MySQL 时，先创建 `task_hub` 数据库及专用应用用户，再导入：

```sh
mysql -h 127.0.0.1 -u task_hub -p task_hub < database/init/001_app_metadata.sql
```

使用上述用户启动后端时设置 `MYSQL_USER=task_hub` 和 `MYSQL_PASSWORD`；Compose 已自动传入，单独启动 Java 时默认用户是 `taskhub`，需按实际用户覆盖。

后续版本升级使用有序增量迁移；不要通过删除数据卷升级或重新初始化已有业务库。此元数据初始化脚本不是迁移系统。
