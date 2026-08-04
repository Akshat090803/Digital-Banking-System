package com.banking.transactionservice.client;

import com.banking.transactionservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

//@FeignClient(name = "account-service", url = "http://localhost:8081/api/v1/accounts") //way -1
@FeignClient(name = "account-service", url = "${account.service.url}")  //way 2
public interface AccountServiceClient {

    //account.service.url (this is defined in application.yaml

//    @PutMapping("/{accountNumber}/debit")
//    ApiResponse<String> debitAmount(
//            @PathVariable String accountNumber,
//            @RequestParam BigDecimal amount
//    );

    @PutMapping("/api/v1/accounts/{accountNumber}/debit")
    ApiResponse<String> debitAmount(
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam BigDecimal amount
    );
}


/**
 * Spring Cloud OpenFeign is a declarative HTTP web service client that simplifies communication between microservices.
 * Instead of writing boilerplate code with RestTemplate or WebClient, you simply create an interface
 * and apply Spring MVC annotations to make external HTTP requests.
 *
 * How It Works
 * Spring automatically generates the runtime implementation for your annotated interface,
 * handling serialization, deserialization, and URL targeting behind the scenes.
 *
 * @FeignClient(name = "account-service", url = "http://localhost:8081/api/v1/accounts")
 * public interface AccountClient {
 *
 *     @GetMapping("/{accountNumber}/balance")
 *     ApiResponse<BigDecimal> getAccountBalance(@PathVariable("accountNumber") String accountNumber);
 * }
 */

//Way 2 (Using property placeholders like ${account.service.url})
//It is best practice because it lets you change target environments
// (e.g., local, staging, production) directly from your application.yaml without editing Java code.
///@FeignClient(name = "account-service", url = "${account.service.url}")
/// public interface AccountServiceClient {
///
/// //    @PutMapping("/{accountNumber}/debit")
/// //    ApiResponse<String> debitAmount(
/// //            @PathVariable String accountNumber,
/// //            @RequestParam BigDecimal amount
/// //    );
///
///     @PutMapping("/api/v1/accounts/{accountNumber}/debit")
///     ApiResponse<String> debitAmount(
///             @PathVariable String accountNumber,
///             @RequestParam BigDecimal amount
///     );
/// }


//Best Practice
///Feign Path Parameter Explicit Mapping:
///In Spring Cloud OpenFeign, it is best practice to explicitly declare the path variable name inside the annotation,
///like @PathVariable("accountNumber") String accountNumber, to guarantee correct mapping across different compilation setups