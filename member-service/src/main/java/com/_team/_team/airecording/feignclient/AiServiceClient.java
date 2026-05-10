package com._team._team.airecording.feignclient;

import com._team._team.airecording.feignclient.dtos.TranscribeResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@FeignClient(
        name = "ai-service",
        url = "${feign.url.ai-service:}",
        configuration = FeignFormConfig.class
)
public interface AiServiceClient {
    @PostMapping(
            value = "/ai/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    TranscribeResDto transcribe(
            @RequestPart("audio") MultipartFile audio,
            @RequestPart("language") String language,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId
    );
}