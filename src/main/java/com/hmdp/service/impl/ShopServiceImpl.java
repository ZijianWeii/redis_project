package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {

        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 从redis中查询商铺缓存
        String shopJsonData = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if(StrUtil.isNotBlank(shopJsonData)){
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJsonData, Shop.class);
            return shop;
        }

        if(shopJsonData != null){
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            //获取互斥锁
            Boolean isLocked = tryLock(lockKey);

            //判断是否获取成功
            if(!isLocked){
                //失败则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //成功，根据id查询数据库
            shop = getById(id);

            //数据库中也不存在，返回错误
            if(shop == null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //存在，把商铺数据写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }

        //返回
        return shop;

    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空！");
        }

        //更新数据库
        updateById(shop);

        //删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
