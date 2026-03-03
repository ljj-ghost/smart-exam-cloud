# Nacos Config Center Setup

This project now loads configuration from Nacos by default:

- `common.yaml`
- `${spring.application.name}.yaml`

All services read these from:

- `group`: `DEFAULT_GROUP` (override with `NACOS_GROUP`)
- `namespace`: default public namespace (override with `NACOS_NAMESPACE`)
- `server`: `192.168.242.10:8848` (override with `NACOS_SERVER_ADDR`)

> Important for Nacos 2.x:
> When services run outside the Nacos container network (for example on host IDE),
> make sure Docker publishes `8848`, `9848`, `9849`.
> `8848` is HTTP API/UI, `9848/9849` are gRPC channels used by Nacos Java client.

## 1. Import Data IDs in Nacos UI

Open `http://192.168.242.10:8848/nacos`, then create these Data IDs in `DEFAULT_GROUP` with type `YAML`:

- `common.yaml`
- `gateway-service.yaml`
- `auth-service.yaml`
- `user-service.yaml`
- `question-service.yaml`
- `exam-service.yaml`
- `grading-service.yaml`
- `analysis-service.yaml`

Use files from this folder as content templates.

## 2. Optional CLI Import (Linux/macOS/Git Bash)

```bash
NACOS_ADDR="http://192.168.242.10:8848"
GROUP="DEFAULT_GROUP"
for f in common.yaml gateway-service.yaml auth-service.yaml user-service.yaml question-service.yaml exam-service.yaml grading-service.yaml analysis-service.yaml; do
  curl -sS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${f}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@docs/nacos/${f}"
done
```

## 3. Runtime Environment Variables

If your Nacos address/account changes, set these before starting services:

- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `NACOS_GROUP`
- `NACOS_NAMESPACE`
