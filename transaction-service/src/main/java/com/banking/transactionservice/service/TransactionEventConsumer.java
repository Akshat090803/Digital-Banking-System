package com.banking.transactionservice.service;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.repository.TransactionRepository;
import com.banking.transactionservice.type.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
     private  final RedisTemplate<String,String> redisTemplate;
     private static final long OTP_EXPIRY_MINUTES=5;
     private final KafkaTemplate<String,Object> kafkaTemplate;
     private final TransactionService transactionService;

     private static final String  VERIFICATION_OTP_GENERATED_TOPIC="verification.otp.generated";

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
        @Payload Map<String,Object> payload
    ){

        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            BigDecimal amount = new BigDecimal(
                    payload.get("amount").toString()
            );
            String reason = (String) payload.get("reason");

            log.info("Verification required - transaction: {}, reason: {}",transactionId,reason);

            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(()->new RuntimeException("Transaction not found "+transactionId));

            if(transaction.getTransactionStatus() != TransactionStatus.PROCESSING){
                log.info("Transaction: {} not in Processing state - skipping",transactionId);
                return;
            }

            //generate OTP
            String otp = String.format("%06d",(int)(Math.random() * 900000)+100000);

            //store in redis for 5 minutes
            String otpKey = "verification:otp"+transactionId;
            redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTES,TimeUnit.MINUTES);

            //Update transaction status
            transaction.setTransactionStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {}, expires in {}min",transactionId,OTP_EXPIRY_MINUTES);


            //Notify Users
            Map<String,Object>  otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("amount",amount);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);

            kafkaTemplate.send(VERIFICATION_OTP_GENERATED_TOPIC,transactionId,otpEvent);


        } catch (Exception e) {
            log.error("Something went wrong in generating otp: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckClean(
            @Payload Map<String,Object> payload
    ){
             try{
                 String transactionId = (String) payload.get("transactionId");

                 transactionService.processCleanResult(transactionId);
             } catch (Exception e) {
                 log.error("Error processing fraud check clean event consumer: {}",e.getMessage());
             }

    }
}

