package com._team._team.company.service;

import com._team._team.dto.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NtsApiService {

    @Value("${nts.api.url}")
    private String ntsApiUrl;

    @Value("${nts.api.key}")
    private String ntsApiKey;

    public void validate(String businessNumber) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String decodedKey = URLDecoder.decode(ntsApiKey, StandardCharsets.UTF_8);
            String url = ntsApiUrl + "/status?serviceKey=" + decodedKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("b_no", Collections.singletonList(
                    businessNumber.replace("-", "")
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ParameterizedTypeReference<Map<String, Object>> responseType =
                    new ParameterizedTypeReference<>() {};

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, responseType
            );

            Map<String, Object> result = response.getBody();
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");

            if (data == null || data.isEmpty()) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "등록되지 않은 사업자번호입니다."
                );
            }

            String bSttCd = (String) data.get(0).get("b_stt_cd");

            if (!"01".equals(bSttCd)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "유효하지 않은 사업자번호입니다."
                );
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("국세청 API 호출 실패: {}", e.getMessage());
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "사업자번호 검증 중 오류가 발생했습니다."
            );
        }
    }
}