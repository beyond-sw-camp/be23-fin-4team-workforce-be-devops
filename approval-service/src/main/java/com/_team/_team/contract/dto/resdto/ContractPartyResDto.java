package com._team._team.contract.dto.resdto;

import com._team._team.contract.domain.ContractParty;
import com._team._team.contract.domain.enums.PartyRole;
import com._team._team.contract.domain.enums.SignStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractPartyResDto {

    private UUID partyId;
    private UUID memberId;
    private PartyRole partyRole;
    private SignStatus signStatus;
    private LocalDateTime signedAt;
    private String signatureImageUrl;

    public static ContractPartyResDto fromEntity(ContractParty entity) {
        return ContractPartyResDto.builder()
                .partyId(entity.getPartyId())
                .memberId(entity.getMemberId())
                .partyRole(entity.getPartyRole())
                .signStatus(entity.getSignStatus())
                .signedAt(entity.getSignedAt())
                .signatureImageUrl(entity.getSignatureImageUrl())
                .build();
    }
}
