-- semantic_cache_evict.lua
-- Redis Lua 原子 LRU 淘汰脚本：超过 max-entries 则淘汰最早条目。
--
-- KEYS[1]: ZSET key (sc:index)
-- ARGV[1]: max entries limit
-- ARGV[2]: hash to add
-- ARGV[3]: timestamp score (epoch millis)

local count = redis.call('ZCARD', KEYS[1])
local max = tonumber(ARGV[1])

if count >= max then
    -- 淘汰 score 最小的条目（最早访问）
    local popped = redis.call('ZPOPMIN', KEYS[1])
    if popped and #popped > 0 then
        local evicted = popped[1]
        redis.call('DEL', 'sc:emb:' .. evicted)
        redis.call('DEL', 'sc:result:' .. evicted)
        redis.call('DEL', 'sc:orig:' .. evicted)
    end
end

redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
return 1
