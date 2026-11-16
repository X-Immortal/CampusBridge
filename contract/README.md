# 契约类型（唯一词汇表）

> 文件：`contract/src/**`。**只有组长能改**；改动必须同时改 `PROTOCOL.md`、`fixtures/` 和测试。
> 四个使用者：鸿蒙客户端（渲染）、后端（生产）、transport（解析）、评测（断言）。

## 为什么单独放在 `contract/`

鸿蒙工程还没建（DevEco 向导要求空目录），但契约必须先冻死，否则张、陆两个人 11/18 之后动不了。
所以契约先在这里独立开发、独立测试，工程建好后一条命令同步进去：

```powershell
# 工程建在 CampusBridge/app 之后
./scripts/sync-contract.ps1
```

同步的四个文件（位置固定，别手工改）：

| 源 | 目标 |
|---|---|
| `src/protocol/types.ts` | `app/entry/src/main/ets/protocol/types.ts` |
| `src/protocol/whitelist.ts` | `app/entry/src/main/ets/protocol/whitelist.ts` |
| `src/transport/events.ts` | `app/entry/src/main/ets/transport/events.ts` |
| `src/transport/sse.ts` | `app/entry/src/main/ets/transport/sse.ts` |

## 命令

```powershell
cd CampusBridge/contract
npm install          # 只装 typescript + @types/node
npm run check        # tsc --noEmit + node --test（改完必须绿）
npm run mock         # 起 mock SSE，默认 :4390
```

## ArkTS 兼容约束（写的时候手别抖）

- 不用 `as const`、`any`、`unknown`、`Record`、索引签名、映射类型、装饰器。
- 不用 `enum`，用字符串字面量联合类型。
- 事件负载用一个 `EventPayload` 接口装所有可选字段，不用判别联合（ArkTS 的收窄不可靠）。
- 这些不是洁癖：文件要同时被 `tsc` 和 ArkTS 编译器吃下去。
