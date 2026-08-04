package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponseDto;
import com.banking.accountservice.dto.CreateAccountRequestDto;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.repository.AccountRepository;
import com.banking.accountservice.type.AccountStatus;
import com.banking.accountservice.util.AccountUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountUtil accountUtil;

    public AccountResponseDto createAccount(@Valid CreateAccountRequestDto request) {
        log.info("Creating Account for {}",request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists for email "+request.getEmail());
        }

        Account newAccount = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .balance(request.getInitialDeposit())
                .accountType(request.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .dailyTransactionLimit(accountUtil.determineDailyTransactionLimit(request.getAccountType()))
                .accountNumber(accountUtil.generateAccountNumber())
                .build();

        Account savedAccount = accountRepository.save(newAccount);
        log.info("Account created: {}",savedAccount.getAccountNumber());

        return accountUtil.mapToAccountResponseDto(savedAccount);

    }

    public AccountResponseDto getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found."));

        return accountUtil.mapToAccountResponseDto(account);

    }

    public BigDecimal getAccountBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found."));

        return account.getBalance();
    }

    /**
     * Block Account - called by fraud detection service via kafka
     * @param accountNumber
     */
    @Transactional
    public void blockAccount(String accountNumber) {
        log.info("Blocking account: {}",accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found."));

        account.setAccountStatus(AccountStatus.BLOCKED);
        log.info("Account blocked: {}",accountNumber);
    }

    /**
     * Debit Amount - debit amount from sender's account balance
     * Called by Transaction service
     * @param accountNumber
     * @param amount
     */
    @Transactional
    public void debitAmount(String accountNumber, BigDecimal amount) {
        log.info("Debiting amount: {} from account: {}",amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found."));

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Transaction failed: Account: "+accountNumber+" is not active.");
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Transaction failed: Insufficient funds.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        log.info("Debited amount: {} from account: {}",amount,accountNumber);
        log.info("Balance updated. New balance: {} ", account.getBalance());
    }

    /**
     * Credit Amount
     * called by transaction service via kafka
     * @param accountNumber
     * @param amount
     */
    @Transactional
    public void creditAmount(String accountNumber, BigDecimal amount) {
        log.info("Crediting amount: {} to account: {}",amount,accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()->new RuntimeException("Account not found."));

        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Transaction failed: Account: "+accountNumber+" is not active.");
        }

        account.setBalance(account.getBalance().add(amount));

        log.info("Credited amount: {} to account: {}",amount,accountNumber);
        log.info("Balance updated. New balance: {} ", account.getBalance());
    }
}
