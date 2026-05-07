package com._team._team.approval.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class OfficialRecipientReqDto {

    @NotNull(message = "수신 부서 ID는 필수입니다.")
    private UUID recipientOrganizationId;
}
