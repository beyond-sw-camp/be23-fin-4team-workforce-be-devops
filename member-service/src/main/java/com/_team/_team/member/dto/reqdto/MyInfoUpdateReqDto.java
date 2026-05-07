package com._team._team.member.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyInfoUpdateReqDto {

    private String phoneNumber;
    private String phonePublicYn;
    private String emergencyContact;
    private String address;
    private String detailAddress;
    private String addressPublicYn;
    private String bank;
    private String bankAccount;
    private String extensionNumber;
    private String telNumber;
}
