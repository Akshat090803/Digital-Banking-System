package com.banking.frauddetectionservice.client;

import com.banking.frauddetectionservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{accountNumber}/balance")
    ApiResponse<BigDecimal> getAccountBalance(@PathVariable("accountNumber") String accountNumber);
}
