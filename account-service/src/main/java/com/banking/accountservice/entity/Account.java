package com.banking.accountservice.entity;

import com.banking.accountservice.type.AccountStatus;
import com.banking.accountservice.type.AccountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts",
indexes = {
        @Index(name = "idx_account_number",columnList = "accountNumber",unique = true)
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    //we created Index on accountNumber in @Table and set unique=true also there
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolderName;

    @Column(nullable = false,unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    ///Java has a BigDecimal class (found in the java.math package) specifically designed
    // for high-precision, signed decimal arithmetic.
    // It is the industry standard for financial calculations, such as account balances
    //or transaction amounts, because it avoids the rounding errors caused by double or float
    @Column(nullable = false,precision = 15 , scale = 2)
    private BigDecimal balance;

    @Column(nullable = false,precision = 15 , scale = 2)
    private BigDecimal dailyTransactionLimit;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}



//! DOn't use @Data annotation in Entity . It breaks in run time . and cuase severe runtime error.
//! Use manual @Gatters, @Setters instead

//Why @Data Breaks JPA Entities
///Broken hashCode():
// @Data generates a hashCode() that uses all fields (including your generated UUID id).
// When a new entity is saved, its id changes from null to a UUID.
// This alters the hash code, which can cause the entity to "disappear" or misbehave
// if stored in a Java Set (like a collection of relationships).
//
/// Performance Overhead:
//The generated toString() and equals() methods automatically
//read every single field. If you add @OneToMany or @ManyToOne relationships later,
// this will trigger unintended database queries or cause infinite recursion loops
// (StackOverflowError).

///Detail explanation
///1. Unintended Database Queries (Lazy Loading Spoilage)
// JPA uses Lazy Loading to optimize performance. For example,
// if your Account has a @OneToMany list of Transaction entities,
// Spring Data won't fetch those transactions from the database until you explicitly ask for them.
// However, @Data generates a toString() method that automatically reads every single field to print its value.
// If you log your Account entity (e.g., logger.info(account)), the generated toString() method is called.
// toString() accesses the lazy-loaded transaction list.
// This forces Hibernate to immediately trigger extra, unintended SQL queries to fetch all transactions,
// destroying your performance optimization.

///2. Infinite Loops (StackOverflowError)
// This happens when you have a bidirectional relationship (two entities referencing each other).
// Imagine this setup:
// Account has a @OneToMany list of Transaction entities.
// Transaction has a @ManyToOne reference back to its parent Account.
// If both classes use @Data:

//Account.toString() is called -> prints its fields -> calls Transaction.toString()
//   ↳ Transaction.toString() is called -> prints its fields -> calls Account.toString()
//       ↳ Account.toString() is called again -> loops infinitely until the application crashes!

///The exact same infinite loop occurs if you call .equals() or .hashCode(), as both methods will try to
//inspect the connected entity, which inspects the original entity, and so on.

///Fix
//To safely include relationships, use individual annotations and explicitly exclude the relational
// fields using @ToString.Exclude and @EqualsAndHashCode.Exclude.