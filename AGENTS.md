# CampusBridge 开发约定

## 文档入口

- `docs/srs.md`：指导开发与验收的业务范围、角色权限、状态规则和验收条件。
- `docs/architecture.md`：系统技术选择、模块职责、数据归属与核心一致性设计。
- `docs/api-guide.md`：当前接口公共结构、调用顺序及动态载荷的兼容说明。
- `docs/development.md`：配置、启动、数据库和验证操作。
- `docs/open-questions.md`：尚未由产品或部署者裁决的规则。
- `backend/sql/schema.sql`：完整数据库结构。
- Controller、DTO 与 `docs/openapi.yaml`：实际接口定义。通过 `OpenApiExportTest` 重新导出，勿手工改生成文件。
- `docs/reviews/`：实现差距、问题、修复证据和验证结果；业务待决项引用 open-questions.md。
- `docs/archive/`：只供追溯的原课程材料，不作为当前实现依据。

## 实施约束

1. 沿用 Vue 3、Spring Boot、MyBatis、MySQL 的现有架构；业务规则以 CampusBridge 的 SRS 和已确认决策为准。
2. 遵循全局工程约定及适用的 `gense-workflow`、`backend-engineering`、`naming-discipline` 技能。按阶段维护文档：SRS 写业务规则和验收；架构写方案、职责和核心机制；接口和运行说明写调用与操作；实现差距和测试事实写评审。代码用于核实基础和发现差距，不能把缺陷反写成规格。
3. 保持现有接口路径和字段。业务取舍或公共契约发生实质变化时，同步前端、后端和规格；尚未明确的产品规则先记录问题。
4. 状态变更、积分、历史和站内通知保持同一业务动作的一致性。并发写入使用条件更新、行锁与数据库约束，并检查影响行数。
5. 使用 WSL 工具链和 WSL Git；Java 编译目标为 17。分支、提交及远端操作遵守本次授权和全局 Git 规范。
6. 私密配置与凭据通过环境变量或已忽略的本地配置提供。测试数据使用明确的非真实账号。
7. 本项目为课程项目，开发与演示数据可重建。数据库结构变更直接更新 `backend/sql/schema.sql`，同步演示数据脚本和相关测试，通过重建数据库验证；不维护增量迁移脚本或旧数据兼容逻辑。
8. 按改动风险执行验证，优先定向测试；权限、事务和并发变更要覆盖拒绝路径与数据库行为。MockMvc 加 mock 服务的测试不能替代真实数据库验收。

运行和验证命令见 `docs/development.md`。报告实际修改、验证结果与未解决问题，不把历史测试结果当作当前结果。
