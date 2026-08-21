package com.pharmasense.notification.service;

import com.pharmasense.notification.client.ZeptoMailClient;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final ZeptoMailClient zeptoMailClient;

    public EmailService(ZeptoMailClient zeptoMailClient) {
        this.zeptoMailClient = zeptoMailClient;
    }

    public void sendOtpCode(String toEmail, String recipientName, String code, int ttlMinutes) {
        String subject = "Your Pharmasense sign-in code: " + code;
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="margin-bottom:4px">Pharmasense</h2>
                  <p>Hi %s,</p>
                  <p>Your one-time sign-in code is:</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:6px;margin:16px 0">%s</p>
                  <p>This code expires in %d minutes. If you didn't request this, you can safely ignore this email.</p>
                </div>
                """.formatted(recipientName, code, ttlMinutes);
        zeptoMailClient.sendEmail(toEmail, recipientName, subject, html);
    }

    public void sendStaffInvite(String toEmail, String recipientName, String pharmacyName, String inviteUrl) {
        String subject = "You've been invited to join " + pharmacyName + " on Pharmasense";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px">
                  <h2 style="margin-bottom:4px">Pharmasense</h2>
                  <p>Hi %s,</p>
                  <p>You've been invited to join <strong>%s</strong> on Pharmasense.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#1F4D3E;color:#fff;text-decoration:none;border-radius:6px">Accept invite</a></p>
                </div>
                """.formatted(recipientName, pharmacyName, inviteUrl);
        zeptoMailClient.sendEmail(toEmail, recipientName, subject, html);
    }
}
