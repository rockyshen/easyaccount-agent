# easyaccount-agent

| 环境 | 地址 |
|------|------|
| Pi 本机 | http://127.0.0.1:8088 · ws://127.0.0.1:8088/ws?token=xxx |
| 公网 | http://118.25.46.207:6088 · ws://118.25.46.207:6088/ws?token=xxx |

先 `POST /api/auth/login` 获取 token；同一用户再次登录会使旧 token 失效（单端）。

部署前在库执行 `scripts/alter_auth_and_user_isolation.sql`（会清空测试 account/flow）。

## Pi 部署

1. 克隆到 `/opt/easyaccount-agent`
2. 复制 `deploy/.env.docker.pi.example` → `deploy/.env.docker.pi`，填写 `SPRING_AI_DASHSCOPE_API_KEY` 与 `DB_*`
3. 执行 `bash deploy/docker-up-pi.sh`

## Jenkins Job

```bash
sudo cp /opt/easyaccount-agent/deploy/jenkins/job-config.xml \
  /var/lib/jenkins/jobs/easyaccount-agent/config.xml
sudo chown jenkins:jenkins /var/lib/jenkins/jobs/easyaccount-agent/config.xml
sudo systemctl restart jenkins
```

## frp（公网 6088 → 本机 8088）

参考 `deploy/frpc-services.toml.example` 追加到 `/etc/frp/frpc.toml` 后 `sudo systemctl restart frpc`。
