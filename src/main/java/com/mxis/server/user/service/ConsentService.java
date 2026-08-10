package com.mxis.server.user.service;

import com.mxis.server.common.enums.ConsentAction;
import com.mxis.server.common.enums.ConsentType;
import com.mxis.server.user.dto.ConsentItem;
import com.mxis.server.user.dto.ConsentStatusResponse;
import com.mxis.server.user.dto.ConsentUpdateRequest;
import com.mxis.server.user.entity.Consent;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.ConsentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final UserService userService;

    public List<ConsentStatusResponse> getStatus(Long userId) {
        return List.of(ConsentType.values()).stream()
                .map(type -> toStatus(userId, type))
                .toList();
    }

    @Transactional
    public List<ConsentStatusResponse> updateConsents(Long userId, ConsentUpdateRequest request) {
        User user = userService.getActiveUser(userId);

        for (ConsentItem item : request.consents()) {
            consentRepository.save(new Consent(user, item.consentType(), item.termsVersion(), item.action()));
        }

        return getStatus(userId);
    }

    private ConsentStatusResponse toStatus(Long userId, ConsentType type) {
        List<Consent> history = consentRepository.findLatestFirstByUserIdAndType(userId, type);
        if (history.isEmpty()) {
            return new ConsentStatusResponse(type, false, null, null);
        }
        Consent latest = history.get(0);
        return new ConsentStatusResponse(
                type,
                latest.getAction() == ConsentAction.AGREED,
                latest.getTermsVersion(),
                latest.getOccurredAt());
    }
}
