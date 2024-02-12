package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.SneakyThrows;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIDWorker redisIDWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    //获取队列中的订单信息
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
//                    );
//                    //判断消息获取是否成功
//                    if(list == null || list.isEmpty()){
//                        //如果获取失败，说明没有消息，继续下一次循环
//                        continue;
//                    }
//                    //如果获取成功，可以下单
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> values = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    createVoucherOrder(voucherOrder);
//                    //ack确认
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理pending list订单异常", e);
//                    handlePendingList();
//                }
//            }
//        }
//    }
//    private void handlePendingList(){
//        while(true){
//            try {
//                //获取pending list中的订单信息
//                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                        Consumer.from("g1", "c1"),
//                        StreamReadOptions.empty().count(1),
//                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
//                );
//                //判断消息获取是否成功
//                if(list == null || list.isEmpty()){
//                    //如果获取失败，说明没有消息，继续下一次循环
//                    break;
//                }
//                //如果获取成功，可以下单
//                MapRecord<String, Object, Object> record = list.get(0);
//                Map<Object, Object> values = record.getValue();
//                VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                createVoucherOrder(order);
//                //ack确认
//                stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//            } catch (Exception e) {
//                log.error("处理pending list订单异常", e);
//            }
//        }
//    }
    private class VoucherOrderHandler implements Runnable{
        @SneakyThrows
        @Override
        public void run() {
            while(true){
                //获取队列中的订单信息
                VoucherOrder order = orderTasks.take();
                proxy.createVoucherOrder(order);
            }
        }
    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        save(voucherOrder);
    }



//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        long orderId = redisIDWorker.nextId("order");
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        //执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString(), String.valueOf(orderId)
//        );
//        //判定结果是否为零，
//        int r = result.intValue();
//        if(r != 0){
//            //如果不是零那就没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        return Result.ok(orderId);
//    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判定结果是否为零，
        int r = result.intValue();
        if(r != 0){
            //如果不是零那就没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        long orderId = redisIDWorker.nextId("order");

        //为零，有资格购买，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        orderTasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
//            //尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        //判断秒杀是否结束
//        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//            //尚未开始
//            return Result.fail("秒杀已经结束！");
//        }
//        //判断库存是否充足
//        if(seckillVoucher.getStock() < 1){
//            return Result.fail("库存不足!");
//        }
//
//        //给用户加锁，保证一人一单
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock RedissonLock = redissonClient.getLock("order:" + userId);
//        //获取锁
//        boolean isLock = RedissonLock.tryLock();
//        if(!isLock){
//            return Result.fail("不允许重复下单！");
//        }
//        try {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            RedissonLock.unlock();
//        }
//    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        //判断用户是否已经存在
//        if (count > 0) {
//            return Result.fail("您已经购买过了!");
//        }
//        //扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)
//                .update();
//        if (!success) {
//            return Result.fail("库存不足!");
//        }
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIDWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        //保存并返回订单id
//        save(voucherOrder);
//        return Result.ok(orderId);
//    }

}
