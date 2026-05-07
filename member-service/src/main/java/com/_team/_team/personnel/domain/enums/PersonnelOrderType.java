package com._team._team.personnel.domain.enums;

/**
 * 인사발령 유형
 *  TRANSFER     부서 이동
 *  PROMOTION    승진 (직급 상향)
 *  DEMOTION     강등 (직급 하향)
 *  REASSIGN     보직 변경 (직책 변경)
 *  ROLE_CHANGE  여러 항목 동시 변경 (부서+직급+직책 등 복합)
 */
public enum PersonnelOrderType {
    TRANSFER,
    PROMOTION,
    DEMOTION,
    REASSIGN,
    ROLE_CHANGE
}
