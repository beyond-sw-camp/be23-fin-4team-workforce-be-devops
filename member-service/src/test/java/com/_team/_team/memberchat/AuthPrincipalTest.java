package com._team._team.memberchat;

import com._team._team.memberchat.config.ChatStompHandler.AuthPrincipal;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPrincipalTest {

    @Test
    void hrAdminAndAuditor_areTreatedAsPrivileged() {
        UUID uid = UUID.randomUUID();
        UUID cid = UUID.randomUUID();

        assertThat(new AuthPrincipal(uid, cid, "HR_ADMIN").isHrAdminOrAuditor()).isTrue();
        assertThat(new AuthPrincipal(uid, cid, "AUDITOR").isHrAdminOrAuditor()).isTrue();
        assertThat(new AuthPrincipal(uid, cid, "EMPLOYEE").isHrAdminOrAuditor()).isFalse();
        assertThat(new AuthPrincipal(uid, cid, "MANAGER").isHrAdminOrAuditor()).isFalse();
    }

    @Test
    void getName_returnsUserIdAsString() {
        UUID uid = UUID.randomUUID();
        AuthPrincipal p = new AuthPrincipal(uid, UUID.randomUUID(), "EMPLOYEE");
        assertThat(p.getName()).isEqualTo(uid.toString());
    }
}
