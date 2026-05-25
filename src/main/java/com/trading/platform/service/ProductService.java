package com.trading.platform.service;

import com.trading.platform.dto.request.CreateProductRequest;
import com.trading.platform.dto.response.ProductResponse;
import com.trading.platform.entity.Product;
import com.trading.platform.exception.ProductNotFoundException;
import com.trading.platform.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Registers a new product with a fixed inventory quantity.
     *
     * @param request contains name and total quantity
     * @return product view with current inventory state
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product(request.name(), request.totalQuantity());
        product = productRepository.save(product);
        log.info("Product created: id={} name='{}' totalQuantity={}",
                product.getId(), product.getName(), product.getTotalQuantity());
        return ProductResponse.from(product);
    }

    /**
     * Returns the current inventory state of a product.
     *
     * @param productId the product to query
     * @throws ProductNotFoundException if no product exists with that ID
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductResponse.from(product);
    }
}
