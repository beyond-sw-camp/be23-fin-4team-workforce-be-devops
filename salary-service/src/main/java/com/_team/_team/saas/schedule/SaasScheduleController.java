package com._team._team.saas.schedule;

import com._team._team.dto.ApiResponse;
import com._team._team.saas.schedule.dto.SaasScheduleResDto;
import com._team._team.saas.schedule.dto.SaasScheduleUpdateReqDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/saas/schedules/salary")
public class SaasScheduleController {

    final SaasScheduleService saasScheduleService;

    @Autowired
    public SaasScheduleController(SaasScheduleService saasScheduleService) {
        this.saasScheduleService = saasScheduleService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<SaasScheduleResDto> result = saasScheduleService.listAll();
        return new ResponseEntity<>(
                ApiResponse.success(result, "스케줄 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // jobKey 는 query parameter
    @PutMapping
    public ResponseEntity<?> updateCron(
            @RequestParam String jobKey,
            @Valid @RequestBody SaasScheduleUpdateReqDto reqDto) {
        saasScheduleService.updateCron(jobKey, reqDto.getCron());
        return new ResponseEntity<>(
                ApiResponse.success(null, "스케줄 수정 성공"),
                HttpStatus.OK
        );
    }

    @PostMapping("/pause")
    public ResponseEntity<?> pause(@RequestParam String jobKey) {
        saasScheduleService.pause(jobKey);
        return new ResponseEntity<>(ApiResponse.success(null, "일시중지 됨"), HttpStatus.OK);
    }

    @PostMapping("/resume")
    public ResponseEntity<?> resume(@RequestParam String jobKey) {
        saasScheduleService.resume(jobKey);
        return new ResponseEntity<>(ApiResponse.success(null, "재개 됨"), HttpStatus.OK);
    }
}
