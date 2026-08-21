package com.pharmasense.notification.client;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.notification.config.ZeptoMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around ZeptoMail's transactional email REST API
 * (https://api.zeptomail.com/v1.1/email). Kept separate from
 * {@code EmailService} so the HTTP/vendor concern can be swapped (e.g. for
 * SES or Postmark) without touching anything that calls EmailService.
 */
@Component
public class ZeptoMailClient {

    private static final Logger log = LoggerFactory.getLogger(ZeptoMailClient.class);

    private final WebClient zeptoMailWebClient;
    private final ZeptoMailProperties properties;

    public ZeptoMailClient(WebClient zeptoMailWebClient, ZeptoMailProperties properties) {
        this.zeptoMailWebClient = zeptoMailWebClient;
        this.properties = properties;
    }

    public void sendEmail(String toEmail, String toName, String subject, String htmlBody) {
        if (!properties.sendEnabled()) {
            log.info("[ZeptoMail disabled] Would send to {} - subject: {}\n{}", toEmail, subject, htmlBody);
            return;
        }

        Map<String, Object> requestBody = Map.of(
                "from", Map.of("address", properties.fromEmail(), "name", properties.fromName()),
                "to", List.of(Map.of("email_address", Map.of("address", toEmail, "name", toName))),
                "subject", subject,
                "htmlbody", htmlBody);

        try {
            zeptoMailWebClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));
        } catch (WebClientResponseException e) {
            log.error("ZeptoMail rejected the send request: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Could not send email right now. Please try again.", e);
        } catch (Exception e) {
            log.error("ZeptoMail call failed", e);
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Could not send email right now. Please try again.", e);
        }
    }
}
