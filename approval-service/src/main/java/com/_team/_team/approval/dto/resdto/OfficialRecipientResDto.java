package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.OfficialRecipient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class OfficialRecipientResDto {

    private UUID recipientId;
    private UUID recipientOrganizationId;
    private String recipientOrganizationName;

    public static OfficialRecipientResDto fromEntity(OfficialRecipient entity) {
        return OfficialRecipientResDto.builder()
                .recipientId(entity.getRecipientId())
                .recipientOrganizationId(entity.getRecipientOrganizationId())
                .recipientOrganizationName(entity.getRecipientOrganizationName())
                .build();
    }
}
