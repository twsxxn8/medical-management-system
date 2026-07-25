-- token_bucket.lua
-- Redis Lua 原子令牌桶算法
--
-- KEYS[1]: 令牌桶 key
-- ARGV[1]: capacity    (桶容量，即突发峰值限制)
-- ARGV[2]: rate        (令牌生成速率，每秒生成 rate 个令牌)
-- ARGV[3]: now_ts      (当前时间戳毫秒，由调用方传入避免多节点时钟差异)
--
-- 返回值:
--   {1, remaining}  — 放行
--   {0, retry_ms}   — 拒绝

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now_ts = tonumber(ARGV[3])

-- 获取当前桶状态
local tokens_str = redis.call('HGET', key, 'tokens')
local last_refill_str = redis.call('HGET', key, 'last_refill')
local tokens = tonumber(tokens_str)
local last_refill = tonumber(last_refill_str)

-- 初始化：桶满
if tokens == nil then
    tokens = capacity
    last_refill = now_ts
end

-- 计算补充令牌数
local elapsed = now_ts - last_refill
if elapsed < 0 then elapsed = 0 end
local refill = math.floor(elapsed * rate / 1000)

if refill > 0 then
    tokens = math.min(tokens + refill, capacity)
    last_refill = now_ts
end

-- 判断是否有可用令牌
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HSET', key, 'tokens', tokens, 'last_refill', last_refill)
    local ttl_sec = math.max(math.ceil(capacity / rate) * 3, 60)
    redis.call('EXPIRE', key, ttl_sec)
    return {1, tokens}
else
    local wait_ms = math.ceil(1000 / rate)
    return {0, wait_ms}
end
