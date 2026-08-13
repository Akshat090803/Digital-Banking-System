package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.dto.ApiResponse;
import com.banking.frauddetectionservice.model.FraudCheckResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object>kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    @Value("${fraud.max-transaction-per-minute}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private int suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    private static final String VERIFICATION_REQUIRED_TOPIC="verification.required";
    private static final String FRAUD_CHECK_CLEAN_TOPIC="fraud.check.clean";

    public void checkTransaction(Map<String, Object> payload) {
        String transactionId= payload.get("transactionId").toString();
        String senderAccountNumber= payload.get("senderAccountNumber").toString();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        //Fetch real balance from the account service
        BigDecimal senderBalance = new BigDecimal(
                accountServiceClient.getAccountBalance(senderAccountNumber)
                        .getData()
                        .toString()
        ) ;

        log.info("Checking transaction: {} account: {} amount: {}",transactionId,senderAccountNumber,senderBalance);

        FraudCheckResults result = performFraudChecks(senderAccountNumber,amount,senderBalance);

        if(result.isFraud()){
            log.info("Suspicious activity detected - account: {} " +
                    "reason - {} - requesting OTP verification.",senderAccountNumber,result.getReason());

            //verification.required event emit -> consume by transaction service
            // -> transaction service generates Otp and -> otp.generated event emit
            // ->consumes by Notification service -> (just like user gets otp in message)
            //-> user sends/enters otp -> verify Otp (transaction service)
            //-> if otp matches transaction verified else transaction cancelled and acc blocked

            Map<String,Object> verificationRequiredEvent = new HashMap<>();
            verificationRequiredEvent.put("transactionId",transactionId);
            verificationRequiredEvent.put("accountNumber",senderAccountNumber);
            verificationRequiredEvent.put("amount",amount);
            verificationRequiredEvent.put("reason",result.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationRequiredEvent);



        }else{

            //if fraud not detected (Transaction is clean)
            // fraud.check.clean event emit
            log.info("Transaction: {} is clean",transactionId);
            Map<String,Object> fraudCheckCleanEvent = new HashMap<>();
            fraudCheckCleanEvent.put("transactionId",transactionId);
            fraudCheckCleanEvent.put("isFraud",false);
            fraudCheckCleanEvent.put("reason",null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_TOPIC,transactionId,fraudCheckCleanEvent);

        }


    }

    private FraudCheckResults performFraudChecks(String senderAccountNumber, BigDecimal amount, BigDecimal senderBalance) {
        //Three types of check will be performed

        //1 velocity check (if transactions counts per Unit time (1min or etc.) exceeds Threshold (5,10 or etc.)
        if(isVelocityExceeded(senderAccountNumber)){
            return new FraudCheckResults(true,"Too many transactions in 60 seconds - Velocity limit exceeded.");

        }

        //Pattern 2 - Amount Check (if transaction amount > X * avg Transaction amount of Account)
        if(isAmountSuspicious(senderAccountNumber,amount)){
            return new FraudCheckResults(true,
                    "Unusual transaction amount - exceeds "+maxTransactionsPerMinute+"x your average.");
        }

        //Pattern 3 - balance check
        if(senderBalance.compareTo(BigDecimal.ZERO) > 0
        && isBalanceCheckedFailed(senderBalance,amount)){
            return new FraudCheckResults(true,
                    "Transaction exceeds the 90% of account balance.");

        }

        return new FraudCheckResults(false,null);
    }

    private boolean isAmountSuspicious(String senderAccountNumber, BigDecimal amount) {
        String avgKey ="fraud:avg_amount_for_account:"+senderAccountNumber;
        String avgAmountStr = redisTemplate.opsForValue().get(avgKey);

        //if no avg amount present for this key ,means first transaction
        //so set sending amount as average
        if(avgAmountStr == null){
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return  false;
            //as this time amount will always be less than threshold (amount * multiplier)
        }

        BigDecimal avgAmount = new BigDecimal(avgAmountStr);
        BigDecimal threshold = avgAmount.multiply(
                BigDecimal.valueOf(suspiciousAmountMultiplier)
        );

        //update running average
        BigDecimal newAvgAmount = (avgAmount.add(amount)).divide(
                BigDecimal.valueOf(2),2, RoundingMode.HALF_UP
        );

        redisTemplate.opsForValue().set(avgKey,newAvgAmount.toString());

        log.info("Amount check - amount: {}, threshold: {}, suspicious: {}",amount,threshold,amount.compareTo(threshold)>0);

        return amount.compareTo(threshold) > 0;
    }

    private boolean isBalanceCheckedFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowed = senderBalance.multiply(
                BigDecimal.valueOf(maxBalancePercentage)
        );

        log.info("Balance check - amount: {}, maxAllowed: {}, suspicious: {}",amount,senderBalance,
                amount.compareTo(maxAllowed) > 0);
        return amount.compareTo(maxAllowed) > 0;
    }

    private boolean isVelocityExceeded(String senderAccountNumber) {
        String key = "fraud:velocity"+senderAccountNumber;

        Long count = redisTemplate.opsForValue().increment(key);

        //first transaction
        if(count!=null && count ==1){
             redisTemplate.expire(key,60, TimeUnit.SECONDS);
             //set key expiration after 60 sec. as we have to check in 1 minute no transaction more than maxTransactionLimit
            //hence after 1st trnx , key will be active for 60sec than expire
        }

        log.info("Velocity check - account: {} count: {}/{}",senderAccountNumber,count,maxTransactionsPerMinute);
        return count!=null && count > maxTransactionsPerMinute;
        //return false if count less than maxTransaction and true is transaction count > maxTransaction
    }
}
