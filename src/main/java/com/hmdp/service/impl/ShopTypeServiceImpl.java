package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        String key = SHOP_LIST;

        // 从redis中查询商铺list
        String jsonArray = stringRedisTemplate.opsForValue().get(key);
        List<ShopType> jsonList = JSONUtil.toList(jsonArray,ShopType.class);

        //判断是否存在
        if (!CollectionUtils.isEmpty(jsonList)) {
            return Result.ok(jsonList);
        }

        //不存在，查询数据库
        List<ShopType> shopTypesFromMysql = query().orderByAsc("sort").list();

        //存在，把商铺list写入redis
        stringRedisTemplate.opsForValue().set("shop-type",JSONUtil.toJsonStr(shopTypesFromMysql));

        //返回
        return Result.ok(shopTypesFromMysql);

    }
}
