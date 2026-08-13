package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "verification.otp.generated")
    public void consumeVerificationOtpGenerated(
            @Payload Map<String,Object> payload
    ){
        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            BigDecimal amount = new BigDecimal(
                    payload.get("amount").toString()
            );
            String reason = (String) payload.get("reason");
            String otp = (String) payload.get("otp");

            sendAlert(accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED",
                    String.format(
                            "Suspicious Activity Detected on ypur account. "+
                                    "Reason: %s "+
                                    "A transaction of %s is pending for verification. "+
                                    "Your OTP is: %s. Valid for 5 minutes. "+
                                    "If this wasn't you - ignore this message.",
                            reason,transactionId,otp
                    )
            );

        } catch (Exception e) {
            log.error("Error sending otp notification: {}",e.getMessage());
        }
    }

    private void sendAlert(String accountNumber, String transactionVerificationRequired, String format) {

    }
}
