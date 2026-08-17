package com.trading.platform.caching;

import com.trading.platform.entity.Product;
import com.trading.platform.exception.ProductNotFoundException;
import com.trading.platform.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@EnableCaching
public class ProductCachingService {

    @Autowired
    private ProductRepository productRepository;

    @Cacheable("product")
    public Product getProduct(UUID productId) {
        doLongRunningTask();
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private void doLongRunningTask() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
