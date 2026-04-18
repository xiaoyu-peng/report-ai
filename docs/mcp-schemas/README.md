# MCP Schema 快照

持久化两套晴天 MCP 的 `tools/list` 完整响应，给代码生成和离线查阅使用。

| 文件 | 来源 URL | 工具数 | 快照时间 |
| --- | --- | --- | --- |
| `search-mcp.tools.json` | https://api-sc.wengegroup.com/search-mcp | 3 | 2026-04-18 11:00 GMT+8 |
| `sass-mcp.tools.json` | https://api-sc.wengegroup.com/sass-mcp | 41 | 2026-04-18 11:00 GMT+8 |

两份文件都是 `initialize → tools/list` 的**原始响应**（只格式化了缩进，未裁剪字段），保留 `description` / `inputSchema` / `required` / `properties` 全部字段，可直接喂给代码生成工具。

## 重新拉取

```bash
curl -sS -X POST https://api-sc.wengegroup.com/search-mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'appkey: OOn05z7m' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  | python3 -m json.tool --no-ensure-ascii \
  > docs/mcp-schemas/search-mcp.tools.json

# sass-mcp 同理，换 URL
```

## 用法示例

```bash
# 列所有工具名
jq -r '.result.tools[].name' docs/mcp-schemas/sass-mcp.tools.json

# 看某个工具的必填参数
jq '.result.tools[] | select(.name=="hot-article") | .inputSchema.required' docs/mcp-schemas/sass-mcp.tools.json

# 按关键字筛工具
jq '.result.tools[] | select(.description | contains("情感"))  | {name, desc: (.description[:80])}' docs/mcp-schemas/sass-mcp.tools.json
```

## 后续接入后端时

`report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/mcp/dto/` 下的 POJO 会按这些 schema 生成，改动时请同时更新这两份 JSON，并在 PR 里贴出 `tools/list` 新旧 diff。