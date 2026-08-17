package com.mxis.server.product.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.dto.ProductRecognizeResponse;
import com.mxis.server.product.dto.ProductRegisterRequest;
import com.mxis.server.product.dto.ProductResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final UserRepository userRepository;
    private final DppCatalogService dppCatalogService;

    public ProductRecognizeResponse recognize(String dppCode) {
        return dppCatalogService.recognize(dppCode);
    }

    @Transactional
    public ProductResponse register(Long userId, ProductRegisterRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = new Product(
                user,
                request.dppCode(),
                request.productName(),
                request.modelCode(),
                request.materialId(),
                request.materialDisplayName(),
                request.materialSubtypes(),
                request.color(),
                request.productImageUrl(),
                request.purchasedAt());

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved, isPrimary(user, saved));
    }

    public List<ProductResponse> getMyProducts(Long userId) {
        User user = getActiveUser(userId);
        return productRepository.findAllActiveByUserId(userId).stream()
                .map(product -> ProductResponse.from(product, isPrimary(user, product)))
                .toList();
    }

    public ProductResponse getProduct(Long userId, Long productId) {
        User user = getActiveUser(userId);
        Product product = getOwnedProduct(userId, productId);
        return ProductResponse.from(product, isPrimary(user, product));
    }

    public ProductResponse getPrimaryProduct(Long userId) {
        User user = getActiveUser(userId);
        Product primaryProduct = user.getPrimaryProduct();
        if (primaryProduct == null || primaryProduct.isDeleted()) {
            return null;
        }
        return ProductResponse.from(primaryProduct, true);
    }

    @Transactional
    public ProductResponse setPrimaryProduct(Long userId, Long productId) {
        User user = getActiveUser(userId);
        Product product = getOwnedProduct(userId, productId);
        user.changePrimaryProduct(product);
        return ProductResponse.from(product, true);
    }

    @Transactional
    public void delete(Long userId, Long productId) {
        User user = getActiveUser(userId);
        Product product = getOwnedProduct(userId, productId);

        // 제품을 삭제하면 연결돼 있던 기기들도 함께 연결 해제 처리한다 (detached_at 기록).
        // 그렇지 않으면 삭제된 제품에 기기가 "연결된 상태"로 남는 정합성 문제가 생긴다.
        for (ProductDevice link : productDeviceRepository.findActiveByProductId(productId)) {
            link.detach();
        }

        user.clearPrimaryProductIf(product);
        product.softDelete();
    }

    private User getActiveUser(Long userId) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isPrimary(User user, Product product) {
        return user.getPrimaryProduct() != null && user.getPrimaryProduct().getId().equals(product.getId());
    }

    Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }
}
