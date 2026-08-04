package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponseDto;
import com.banking.accountservice.dto.ApiResponse;
import com.banking.accountservice.dto.CreateAccountRequestDto;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponseDto>> createAccount(
            @Valid @RequestBody CreateAccountRequestDto request
            ){
        AccountResponseDto account=  accountService.createAccount(request);
        ApiResponse<AccountResponseDto> response= new ApiResponse<>(
                "Account Created Successfully",
                HttpStatus.CREATED.value(),
                account);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping("/{accountNumber}")
    public  ResponseEntity<ApiResponse<AccountResponseDto>> getAccount(
        @PathVariable String accountNumber
    ){
        AccountResponseDto account = accountService.getAccount(accountNumber);
        ApiResponse<AccountResponseDto> response = new ApiResponse<>(
                "Account retrieved successfully",
                HttpStatus.OK.value(),
                account
        );
        return  ResponseEntity.ok(response);
    }

    @GetMapping("/{accountNumber}/balance")
    public  ResponseEntity<ApiResponse<BigDecimal>> getAccountBalance(
            @PathVariable String accountNumber
    ){
        BigDecimal balance = accountService.getAccountBalance(accountNumber);
        ApiResponse<BigDecimal> response = new ApiResponse<>(
                "Account Balance retrieved successfully",
                HttpStatus.OK.value(),
                balance
        );
        return  ResponseEntity.ok(response);
    }


    @PutMapping("/{accountNumber}/block")
    public  ResponseEntity<ApiResponse<String>> blockAccount(
            @PathVariable String accountNumber
    ){
        accountService.blockAccount(accountNumber);
        ApiResponse<String> response = new ApiResponse<>(
                "Account Blocked successfully",
                HttpStatus.OK.value(),
                "Account Blocked successfully"
        );
        return  ResponseEntity.ok(response);
    }


/*
* Saga step - 1 (Deduct balance from sender's account)
* Called by transaction service when transfer is initiated
* */

    @PutMapping("/{accountNumber}/debit")
    public  ResponseEntity<ApiResponse<String>> debitAmount(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){
        accountService.debitAmount(accountNumber,amount);
        ApiResponse<String> response = new ApiResponse<>(
                "Amount debited successfully",
                HttpStatus.OK.value(),
                "Rs"+amount+" debited successfully from Account: "+accountNumber

        );
        return  ResponseEntity.ok(response);
    }





    /*
     * Saga step - 4 (Compensate Amount (credit to receiver or refund to the sender)
     * Called by transaction service in Two scenarios
     * 1) Fraud detected -> refund to sender (undo step 1)
     * 2) Transaction completed -> credit to receiver
     * */

    @PutMapping("/{accountNumber}/credit")
    public  ResponseEntity<ApiResponse<String>> creditAmount(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){
        accountService.creditAmount(accountNumber,amount);
        ApiResponse<String> response = new ApiResponse<>(
                "Amount credited successfully",
                HttpStatus.OK.value(),
                "Rs"+amount+" credited successfully in Account: "+accountNumber

        );
        return  ResponseEntity.ok(response);
    }






}

