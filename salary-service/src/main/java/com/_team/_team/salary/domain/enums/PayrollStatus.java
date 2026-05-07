package com._team._team.salary.domain.enums;
/**
 * 급여 대장 상태
 * - DRAFT : 작성 중 (수정 가능)
 * - CONFIRMED : 확정 완료 (수정 불가)
 * - PAID : 지급 완료
 */
public enum PayrollStatus {
   DRAFT,
   CONFIRMED,
   PAID
}
