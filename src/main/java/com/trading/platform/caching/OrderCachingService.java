package com.trading.platform.caching;

import com.trading.platform.entity.Order;
import com.trading.platform.exception.OrderNotFoundException;
import com.trading.platform.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@EnableCaching
public class OrderCachingService {

    @Autowired
    private OrderRepository orderRepository;

    @Cacheable("order")
    public Order getOrder(UUID orderId) {
        doLongRunningTask();
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }


    private void doLongRunningTask() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
