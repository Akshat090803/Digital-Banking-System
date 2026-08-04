package com.banking.accountservice.util;

import com.banking.accountservice.dto.AccountResponseDto;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.repository.AccountRepository;
import com.banking.accountservice.type.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Component
@Validated
@RequiredArgsConstructor
public class AccountUtil {

    private final SecureRandom secureRandom = new SecureRandom();
    private final AccountRepository accountRepository;
    public BigDecimal determineDailyTransactionLimit(@NotNull(message = "Account type is required.") AccountType accountType) {
        return accountType == AccountType.SAVINGS ?
                new BigDecimal("100000") : new BigDecimal("500000");
    }

    public String generateAccountNumber() {
        String accountNumber;

        //this appraoch  works perfectly for small-to-medium datasets. However, as your database grows to millions of accounts,
        // the risk of collisions increases, which will cause the loop to run multiple times and slow down your API requests.
        /// see comments at the end of the file for other options
        do {
            //set a upper bound to 10^12 so number would generate till 10^12 - 1 (max 12 digit 999999999999)
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d",number); //format it into a 12 digit length padding 0 at starting
        }while (accountRepository.existsByAccountNumber(accountNumber));//this condition ensure that generated accNumber don't exist in the database.
        //means loop will keep running when generated accNumber exist and stop when generated AccNumber don't exist

        return accountNumber;
    }

    public AccountResponseDto mapToAccountResponseDto(Account account) {
        return  AccountResponseDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .balance(account.getBalance())
                .dailyTransactionLimit(account.getDailyTransactionLimit())
                .createdAt(account.getCreatedAt())
                .build();
    }
}





///Account number generation notes
//If you anticipate extremely high transaction volume, would you like to see how to replace this
// database loop with a synchronized sequential prefix sequence to avoid performance bottlenecks

//To replace the database loop, you can use a thread-safe in-memory generator or a database sequence
// combined with a fixed prefix.However, standard Java JVM synchronization only works on a single server instance.
// If your microservice scales to multiple instances, two servers could generate identical numbers.

///The Best Solution: Distributed Atomic Counter (Redis or Database Sequence)
/// Using an optimized sequence or Redis counter avoids looping the database completely.
//@Component
//@RequiredArgsConstructor
//public class AccountNumberGenerator {
//
//    private final StringRedisTemplate redisTemplate;
//    private static final String PREFIX = "BNK"; // 3-character branch/bank prefix
//
//    public String generateAccountNumber() {
//        // Increment atomic counter globally across all server nodes
//        Long sequence = redisTemplate.opsForValue().increment("account:number:seq");
//
//        // Format to a fixed 9-digit padded number (Total = 12 characters: BNK000000001)
//        return PREFIX + String.format("%09d", sequence);
//    }
//}

//you need the Redis starter dependency in your pom.xml or build.gradle file to use StringRedisTemplate.
////Redis guarantees uniqueness across different instances and server restarts because it acts as a centralised, external source of truth.


/// Option B: Pure Java AtomicLong (Single Server Only)
/// If you only run one instance of your application, you can use an AtomicLong initialize-on-startup pattern.
//@Component
//public class AccountNumberGenerator {
//    // Thread-safe atomic counter
//    private final AtomicLong counter = new AtomicLong(100000000000L);
//
//    public synchronized String generateAccountNumber() {
//        long nextValue = counter.incrementAndGet();
//        return String.valueOf(nextValue); // Guarantees unique, sequential 12-digit string
//    }
//}

