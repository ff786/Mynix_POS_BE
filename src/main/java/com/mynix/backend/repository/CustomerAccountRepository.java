package com.mynix.backend.repository;

import com.mynix.backend.model.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {
}
