@counter @critical
Feature: 分桶高并发计数系统

  采用 Redis 分桶 INCR + 异步批量刷 DB 的架构，统一覆盖点赞、关注、阅读、评论、转发五类计数场景。
  核心目标：热点分散、原子写入、最终一致性、故障降级。

  Background:
    Given 计数器系统初始化完成
    And Redis 集群正常运行
    And MySQL 数据库正常运行

  # ============================================================
  # 场景一：写 —— 分桶写入分散热点
  # ============================================================

  Rule: 所有计数写入分散到不同桶，避免单 key 热点

    @smoke @happy-path
    Scenario: 用户点赞，写入命中一个桶
      Given 实体 "knowpost" ID 为 "310000000000000001" 的计数器已初始化
      When 用户 "100" 对实体执行 "like" 操作，delta 为 1
      Then Redis key "cnt:knowpost:310000000000000001:bucket:{hash%100}" 的 "like" 字段应当 +1
      And 同一个实体的其他 99 个桶的 "like" 字段不变

    Scenario: 多个用户并发点赞，写入分散到不同桶
      Given 实体 "knowpost" ID 为 "310000000000000001" 的计数器已初始化
      When 100 个不同用户同时对实体执行 "like" 操作
      Then 写入应当分布到超过 50 个不同的桶
      And 所有桶的 "like" 字段 SUM 等于 100

    Scenario: 同一用户重复点赞，Lua 脚本保证幂等
      Given 用户 "100" 已经对实体 "310000000000000001" 点过赞
      When 用户 "100" 再次对该实体执行 "like" 操作
      Then Redis bitmap 对应位不变
      And 计数器不增加
      And 返回 changed=false

  # ============================================================
  # 场景二：读 —— SUM 所有桶
  # ============================================================

  Rule: 读计数时汇总所有桶的值，不查 DB

    Scenario: 读取单个实体的单个计数
      Given 实体 "knowpost" ID 为 "310000000000000001" 的 100 个桶中
        | 桶号 | like |
        |  12  |  5   |
        |  47  |  3   |
        |  88  |  2   |
      When 读取该实体的 "like" 计数
      Then 返回值应当为 10

    Scenario: 读取单个实体的所有计数（一次 HGETALL 拿所有 metric）
      Given 实体 "knowpost" ID 为 "310000000000000001" 的桶 0 存储
        | like | fav  | comment |
        |  10  |  3   |    7    |
      And 桶 1 存储
        | like | fav  | comment |
        |   5  |  2   |    3    |
      When 读取该实体的所有计数
      Then like 返回 15
      And fav 返回 5
      And comment 返回 10

    Scenario: 新实体没有数据，SUM 空桶返回 0
      Given 实体 "knowpost" ID 为 "999" 没有任何桶数据
      When 读取该实体的 "like" 计数
      Then 返回值应当为 0

  # ============================================================
  # 场景三：异步批量刷 DB
  # ============================================================

  Rule: 计数器增量异步批量写回 MySQL，不阻塞用户请求

    Scenario: Kafka 消费者批量刷 DB
      Given 实体 "knowpost" ID 为 "310000000000000001" 的桶中
        | 桶号 | like 增量 |
        |  12  |    +5     |
        |  47  |    +3     |
      And MySQL 中该实体的 like 计数当前为 20
      When Kafka 消费者拉取到 2 条增量事件并执行批量刷库
      Then MySQL 中该实体的 "like" 计数应当更新为 28
      And 对应的 Redis 桶的 pending 标记应当被清除

    Scenario: 刷库失败时保留 Redis 数据，触发重试
      Given MySQL 连接超时
      When Kafka 消费者尝试刷库
      Then Redis 桶数据不被清除
      And 消费者进行重试
      And 告警被触发

    Scenario: 队列积压时启用背压
      Given Kafka 消费 lag 超过 10000
      When 新的计数增量事件到达
      Then 不再写入 Kafka 队列
      And 降级为同步 Redis INCR 写入（不刷 DB）
      And 告警被触发

  # ============================================================
  # 场景四：降级 —— Redis 故障走 DB
  # ============================================================

  Rule: Redis 不可用时降级为 DB 原子更新，牺牲吞吐保正确

    Scenario: Redis 故障降级走 DB
      Given Redis 连接超时
      When 用户 "100" 对实体执行 "like" 操作
      Then 降级走 MySQL 原子更新 "UPDATE counter SET count = count + 1"
      And 返回成功
      And 日志记录降级事件

    Scenario: Redis 恢复后从 bitmap 重建计数
      Given Redis 之前故障，现在已恢复
      And Redis 中计数器数据已丢失
      When 触发计数重建
      Then 从 bitmap 中 BITCOUNT 重建所有计数字段
      And 重建后的计数与 bitmap 中的事实一致
      And 日志记录重建完成

    Scenario: Redis + DB 全挂时熔断
      Given Redis 和 MySQL 均不可用
      When 用户尝试执行计数操作
      Then 返回熔断错误
      And 不阻塞主业务流程

  # ============================================================
  # 场景五：覆盖五个业务场景
  # ============================================================

  Rule: 同一套分桶抽象覆盖点赞、收藏、评论、阅读、关注

    Scenario Outline: 五个场景使用统一接口
      Given 计数器系统已初始化
      When 对 entityType "<entityType>" entityId "<entityId>" 执行 "<metric>" 操作
      Then Redis 桶 "<expectedBucketPattern>" 的 "<metric>" 字段应当 +1

      Examples:
        | entityType | entityId               | metric     | expectedBucketPattern      |
        | knowpost   | 310000000000000001     | like       | cnt:knowpost:...:bucket:*  |
        | knowpost   | 310000000000000001     | fav        | cnt:knowpost:...:bucket:*  |
        | knowpost   | 310000000000000001     | comment    | cnt:knowpost:...:bucket:*  |
        | knowpost   | 310000000000000001     | view       | cnt:knowpost:...:bucket:*  |
        | user       | 6                      | followings | cnt:user:...:bucket:*      |
        | user       | 6                      | followers  | cnt:user:...:bucket:*      |

  # ============================================================
  # 场景六：关注计数的完整流程
  # ============================================================

    Scenario: A 关注 B，双方计数正确更新
      Given 用户 A (id=100) 的关注数为 5
      And 用户 B (id=200) 的粉丝数为 10
      When 用户 A 关注用户 B
      Then 用户 A 的 followings 计数应当为 6
      And 用户 B 的 followers 计数应当为 11

    Scenario: A 取消关注 B，双方计数正确回退
      Given 用户 A 已关注用户 B
      When 用户 A 取消关注用户 B
      Then 用户 A 的 followings 计数应当 -1
      And 用户 B 的 followers 计数应当 -1
