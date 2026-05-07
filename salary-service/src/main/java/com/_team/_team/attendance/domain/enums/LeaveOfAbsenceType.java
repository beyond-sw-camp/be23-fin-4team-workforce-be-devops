package com._team._team.attendance.domain.enums;

/**
 * 휴직 종류
 * paidByLaw, 법정 유급 여부
 */
public enum LeaveOfAbsenceType {
    MATERNITY(true,  true),    // 출산휴가
    PATERNAL(true,   true),    // 육아휴직
    SICK(false,      false),   // 장기병가
    UNPAID(false,    false),   // 무급휴직
    STUDY(false,     false),   // 학업휴직
    MILITARY(true,   true);    // 군복무

    private final boolean countsAsWorkday;
    private final boolean paidByLaw;

    LeaveOfAbsenceType(boolean countsAsWorkday, boolean paidByLaw) {
        this.countsAsWorkday = countsAsWorkday;
        this.paidByLaw = paidByLaw;
    }

    public boolean countsAsWorkday() {
        return countsAsWorkday;
    }

    public boolean isPaidByLaw() {
        return paidByLaw;
    }
}