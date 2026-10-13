# 启动时演示题初始化外键错误修复

## 问题原因

`SeedQuestionDataConfig` 原先将所有演示题的 `creator_id` 固定为 `1`，但用户表 `sys_user` 的自增 ID 不保证从 1 开始，也不保证当前数据库仍有 `id = 1` 的用户。`question.creator_id` 又通过 `fk_question_creator` 外键引用 `sys_user(id)`，因此会导致 Spring Boot 在执行 `CommandLineRunner` 时启动失败。

此外，用户初始化与演示题初始化原先没有明确排序；首次初始化数据库时，题目初始化可能早于演示用户初始化。

## 修复方案

- 增加 `OrderedCommandLineRunner`，显式控制初始化顺序。
- `SeedDataConfig` 顺序为 1：先创建或补齐管理员、教师和学生账户。
- `SeedQuestionDataConfig` 顺序为 2：再检查并初始化演示题。
- 演示题创建者优先选择启用的 `teacher` 用户，其次选择启用的 `admin` 用户，最后选择任意启用用户。
- 所有演示题使用查询到的真实用户 ID，不再假定 `creator_id = 1`。
- 若数据库没有任何启用用户，记录警告并跳过演示题初始化，不让演示数据阻止应用启动。
- 增加回归测试，覆盖 runner 顺序、真实用户 ID、无启用用户时安全跳过三种情况。

## 应用方式

将补丁中的文件按原路径覆盖到项目，然后在 `backend` 目录运行：

```powershell
mvn -Dtest=SeedQuestionDataConfigTest test
mvn clean test
```

该修复不修改数据库结构，也不删除现有用户、题目或考试数据。
