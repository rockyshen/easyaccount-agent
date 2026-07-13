# easyaccount-agent

| 环境 | API / WebSocket |
|------|-----------------|
| Pi 本机 | http://127.0.0.1:8088 · ws://127.0.0.1:8088/ws?userId=xxx |
| 公网 | http://118.25.46.207:6088 · ws://118.25.46.207:6088/ws?userId=xxx |

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
