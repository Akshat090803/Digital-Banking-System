package com.banking.accountservice.dto;

import com.banking.accountservice.type.AccountStatus;
import com.banking.accountservice.type.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountResponseDto {

    private String id;
    private String accountNumber;
    private String accountHolderName;
    private String email;
    private String phone;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private BigDecimal balance;
    private BigDecimal dailyTransactionLimit;
    private LocalDateTime createdAt;

}



/// We can use @Data on DTOs (Data Transfer Objects).
//Since DTOs are simple data containers without database logic,
// they do not suffer from the JPA issues (like lazy-loading bugs or infinite recursion)
// discussed earlier. @Data is perfect here because it quickly generates your getters,
// setters, toString, equals, and hashCode automatically.

/// immutable DTOs, you have two excellent modern options:
//Lombok's @Value annotation or native Java Records.


///Option A: Lombok @Value
//The @Value annotation is the immutable equivalent of @Data.
// It automatically makes all fields private final, removes setters,
// and generates getters, toString(), equals(), hashCode(),
// and an all-arguments constructor.
//import lombok.Value;
//
//@Value
//public class AccountDto {
//    String id;
//    String accountHolder;
//}

/// Option B: Java Records (Recommended)
//If you are using Java 16 or higher, native Records are the cleanest industry-standard approach.
// They require no external libraries, are inherently immutable, and automatically generate constructors,
// accessors, toString(), equals(), and hashCode().

//public record AccountDto(
//        String id,
//        String accountHolder
//) {}
/// Note: Record accessor methods don't use the "get" prefix; you would call dto.id() instead of dto.getId()).
