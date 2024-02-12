-- 锁的key
local key = KEYS[1]

-- 当前线程标识
local threadId = ARGV[1]


-- 获取锁中的线程标识 get key
local id = redis.call('get', key)

-- 比较获得的标识是否与当前线程的标识一致
if(id == threadId) then
	redis.call('del', key)
end
return 0