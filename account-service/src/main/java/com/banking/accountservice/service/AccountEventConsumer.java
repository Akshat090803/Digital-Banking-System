package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /**
     * Consume transaction.completed event from kafka
     * Transaction completed means everything is completed and No Fraud is detected
     * Hence credit the amount to receiver's account
     * @param payload
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String,Object> payload
            ){
             try{
               String receiverAccount = (String) payload.get("receiverAccountNumber");
               BigDecimal amount = new BigDecimal(payload.get("amount").toString());
               accountService.creditAmount(receiverAccount,amount);
             } catch (Exception e) {
                 log.error("Error crediting account: {}",e.getMessage());
             }
    }

    /**
     * Consume fraud.detected event from kafka
     * Block the flagged account
     * @param paylaod
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String,Object> paylaod
    ){

        try{
                String accountNumber = paylaod.get("accountNumber").toString();
                log.info("Fraud Detected - Blocking account: {}",accountNumber);
                accountService.blockAccount(accountNumber);
        } catch (Exception e) {
            log.info("Error in blocking the account: {}",e.getMessage());
        }
    }


}
