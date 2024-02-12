--- 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
---local id = ARGV[3]


--- 数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

--- 1.判断库存是否充足 get stockKey
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
--- 2.判断用户是否下单 sismember orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 存在，说明是重复下单
    return 2
end
--- 3.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
--- 4.下单(保存用户) sadd orderKey userId
redis.call('sadd', orderKey, userId)
--- 成功，发送消息到stream队列当中，返回零 xadd stream.orders * k1 v1 k2 v2 ...
---redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'orderId', id)
return 0



