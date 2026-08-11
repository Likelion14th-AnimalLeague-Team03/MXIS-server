package com.mxis.server.care.service;

import com.mxis.server.care.dto.CareSuggestionResponse;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareSuggestionService {

    private final CareSuggestionRepository careSuggestionRepository;
    private final ProductRepository productRepository;

    /** 활성 제안이 없으면 null을 반환한다 (에러가 아니라 정상 200 + data:null). */
    public CareSuggestionResponse getActive(Long userId, Long productId) {
        getOwnedProduct(userId, productId);
        return careSuggestionRepository.findLatestActiveByProductId(productId)
                .map(CareSuggestionResponse::from)
                .orElse(null);
    }

    /** 상세 조회는 확인한 것으로 간주해 자동으로 읽음 처리한다. */
    @Transactional
    public CareSuggestionResponse getDetail(Long userId, Long suggestionId) {
        CareSuggestion suggestion = getOwnedSuggestion(userId, suggestionId);
        suggestion.markRead();
        return CareSuggestionResponse.from(suggestion);
    }

    /** 상세를 열지 않고 목록/푸시에서 바로 읽음만 표시하는 보조 경로. */
    @Transactional
    public CareSuggestionResponse.ReadResult markRead(Long userId, Long suggestionId) {
        CareSuggestion suggestion = getOwnedSuggestion(userId, suggestionId);
        suggestion.markRead();
        return CareSuggestionResponse.ReadResult.from(suggestion);
    }

    private CareSuggestion getOwnedSuggestion(Long userId, Long suggestionId) {
        CareSuggestion suggestion = careSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_FOUND));
        if (!suggestion.getProduct().isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_OWNED);
        }
        return suggestion;
    }

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }
}
