package com._team._team.company.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender javaMailSender;

    @Autowired
    public MailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    // 임시 비밀번호 발송 - 메일 실패 추가
    public void sendTempPassword(String toEmail, String companyEmail, String tempPassword) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Workforce] 계정 안내");
            helper.setText(buildTempPasswordHtml(companyEmail, tempPassword), true);
            javaMailSender.send(mimeMessage);
            log.info("임시 비밀번호 메일 발송 완료: {}", toEmail);
        } catch (Exception e) {
            log.warn("임시 비밀번호 메일 발송 실패 - 무시 to={} reason={}", toEmail, e.getMessage());
        }
    }

    // 이메일 인증 코드 발송
    public void sendVerificationCode(String toEmail, String code) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Workforce] 이메일 인증 코드 안내");
            helper.setText(buildVerificationCodeHtml(code), true);
            javaMailSender.send(mimeMessage);
            log.info("인증 코드 메일 발송 완료: {}", toEmail);
        } catch (Exception e) {
            log.info("메일 발송 실패!");
            throw new RuntimeException(e);
        }
    }

    private String buildTempPasswordHtml(String companyEmail, String tempPassword) {
        return "<!DOCTYPE html>"
                + "<html lang='ko'>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='margin: 0; padding: 0; background-color: #f4f7f6;'>"
                + "<div style='width: 100%; max-width: 600px; margin: 40px auto; background-color: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;'>"
                + "<div style='background-color: #0056b3; color: #ffffff; padding: 30px 40px;'>"
                + "<h1 style='margin: 0; font-size: 26px;'>Workforce 계정 안내</h1>"
                + "</div>"
                + "<div style='padding: 40px;'>"
                + "<p style='font-size: 16px; color: #333;'>안녕하세요.<br>Workforce 계정이 생성되었습니다.</p>"
                + "<div style='background-color: #f9f9f9; border: 1px dashed #ccc; border-radius: 6px; padding: 25px; margin: 30px 0;'>"
                + "<p style='margin: 0; font-size: 16px; color: #555;'>회사 이메일:</p>"
                + "<strong style='display: block; margin-top: 10px; font-size: 20px; color: #0056b3;'>"
                + companyEmail
                + "</strong>"
                + "<p style='margin: 20px 0 0; font-size: 16px; color: #555;'>임시 비밀번호:</p>"
                + "<strong style='display: block; margin-top: 10px; font-size: 20px; color: #0056b3; letter-spacing: 2px;'>"
                + tempPassword
                + "</strong>"
                + "</div>"
                + "<p style='font-size: 16px; color: #333;'>보안을 위해 로그인 후 <strong style='color: #D93025;'>즉시 비밀번호를 변경</strong>해 주세요.</p>"
                + "</div>"
                + "<div style='background-color: #f9f9f9; color: #888; padding: 20px 40px; text-align: center; border-top: 1px solid #e0e0e0;'>"
                + "<p style='margin: 0; font-size: 12px;'>본 메일은 발신 전용입니다.</p>"
                + "<p style='margin: 5px 0 0; font-size: 12px;'>&copy; 2025 Workforce. All rights reserved.</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    private String buildVerificationCodeHtml(String code) {
        return "<!DOCTYPE html>"
                + "<html lang='ko'>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='margin: 0; padding: 0; background-color: #f4f7f6;'>"
                + "<div style='width: 100%; max-width: 600px; margin: 40px auto; background-color: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;'>"
                + "<div style='background-color: #0056b3; color: #ffffff; padding: 30px 40px;'>"
                + "<h1 style='margin: 0; font-size: 26px;'>이메일 인증</h1>"
                + "</div>"
                + "<div style='padding: 40px;'>"
                + "<p style='font-size: 16px; color: #333;'>안녕하세요.<br>아래 인증 코드를 입력해주세요. 인증 코드는 5분간 유효합니다.</p>"
                + "<div style='background-color: #f9f9f9; border: 1px dashed #ccc; border-radius: 6px; padding: 25px; margin: 30px 0; text-align: center;'>"
                + "<p style='margin: 0; font-size: 16px; color: #555;'>인증 코드:</p>"
                + "<strong style='display: block; margin-top: 10px; font-size: 28px; color: #0056b3; letter-spacing: 8px;'>"
                + code
                + "</strong>"
                + "</div>"
                + "</div>"
                + "<div style='background-color: #f9f9f9; color: #888; padding: 20px 40px; text-align: center; border-top: 1px solid #e0e0e0;'>"
                + "<p style='margin: 0; font-size: 12px;'>본 메일은 발신 전용입니다.</p>"
                + "<p style='margin: 5px 0 0; font-size: 12px;'>&copy; 2025 Workforce. All rights reserved.</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}