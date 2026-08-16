package com.mynix.backend.repository;

import com.mynix.backend.model.Cheque;
import com.mynix.backend.model.ChequeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChequeRepository
        extends JpaRepository<Cheque, Long> {

    List<Cheque>
    findByCustomerIdOrderByChequeDateDesc(Long customerId);

    List<Cheque>
    findAllByOrderByChequeDateAsc();

    List<Cheque>
    findByStatusOrderByChequeDateAsc(ChequeStatus status);
}