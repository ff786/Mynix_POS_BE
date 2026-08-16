package com.mynix.backend.service.impl;

import com.mynix.backend.dto.customer.ChequeRequest;
import com.mynix.backend.dto.customer.ChequeResponse;
import com.mynix.backend.dto.customer.ChequeStatusRequest;
import com.mynix.backend.model.Cheque;
import com.mynix.backend.model.ChequeStatus;
import com.mynix.backend.model.Customer;
import com.mynix.backend.repository.ChequeRepository;
import com.mynix.backend.repository.CustomerRepository;
import com.mynix.backend.service.ChequeService;
import com.mynix.backend.model.CustomerTransaction;
import com.mynix.backend.model.CustomerTransactionType;
import com.mynix.backend.repository.CustomerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChequeServiceImpl implements ChequeService {

    private final ChequeRepository chequeRepository;
    private final CustomerRepository customerRepository;

    private final CustomerTransactionRepository transactionRepository;

    @Override
    public ChequeResponse create(
            Long customerId,
            ChequeRequest request
    ) {
        Customer customer =
                customerRepository.findById(customerId)
                        .orElseThrow(() -> new RuntimeException("Customer not found."));

        if (!customer.getActive()) {
            throw new RuntimeException("Customer is inactive.");
        }

        Cheque cheque = Cheque.builder()
                .customer(customer)
                .amount(request.getAmount())
                .chequeNumber(
                        request.getChequeNumber().trim()
                )
                .chequeDate(request.getChequeDate())
                .receivedDate(LocalDate.now())
                .depositDate(request.getDepositDate())
                .bankName(
                        request.getBankName() != null
                                ? request.getBankName().trim()
                                : null
                )
                .status(ChequeStatus.RECEIVED)
                .notes(
                        request.getNotes() != null
                                ? request.getNotes().trim()
                                : null
                )
                .build();

        return map(chequeRepository.save(cheque));
    }

    @Override
    @Transactional(readOnly = true)
    public ChequeResponse getById(Long id) {

        return map(chequeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cheque not found."))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChequeResponse> getAll() {

        return chequeRepository
                .findAllByOrderByChequeDateAsc()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChequeResponse> getByCustomer(
            Long customerId
    ) {

        if (!customerRepository.existsById(customerId)) {

            throw new RuntimeException(
                    "Customer not found."
            );
        }

        return chequeRepository
                .findByCustomerIdOrderByChequeDateDesc(
                        customerId
                )
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public ChequeResponse updateStatus(
            Long id,
            ChequeStatusRequest request
    ) {

        Cheque cheque =
                chequeRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Cheque not found."
                                )
                        );

        ChequeStatus currentStatus =
                cheque.getStatus();

        ChequeStatus newStatus =
                request.getStatus();

        /*
         * Already credited = final state.
         */
        if (currentStatus == ChequeStatus.CREDITED) {

            throw new RuntimeException(
                    "A credited cheque cannot be changed."
            );
        }

        /*
         * Already bounced = final state.
         */
        if (currentStatus == ChequeStatus.BOUNCED) {

            throw new RuntimeException(
                    "A bounced cheque cannot be changed."
            );
        }

        /*
         * RECEIVED -> DEPOSITED
         */
        if (newStatus == ChequeStatus.DEPOSITED) {

            if (currentStatus != ChequeStatus.RECEIVED) {

                throw new RuntimeException(
                        "Only a received cheque can be deposited."
                );
            }

            cheque.setDepositDate(
                    request.getDepositDate() != null
                            ? request.getDepositDate()
                            : LocalDate.now()
            );
        }

        /*
         * RECEIVED/DEPOSITED -> CREDITED
         */
        if (newStatus == ChequeStatus.CREDITED) {

            if (currentStatus != ChequeStatus.RECEIVED &&
                    currentStatus != ChequeStatus.DEPOSITED) {

                throw new RuntimeException(
                        "Only a received or deposited cheque can be credited."
                );
            }

            cheque.setBounceReason(null);

            CustomerTransaction transaction =
                    CustomerTransaction.builder()
                            .customer(cheque.getCustomer())
                            .type(CustomerTransactionType.PAYMENT)
                            .amount(cheque.getAmount())
                            .description(
                                    "Cheque credited - "
                                            + cheque.getChequeNumber()
                            )
                            .build();

            transactionRepository.save(transaction);
        }

        /*
         * RECEIVED/DEPOSITED -> BOUNCED
         */
        if (newStatus == ChequeStatus.BOUNCED) {

            if (currentStatus != ChequeStatus.RECEIVED &&
                    currentStatus != ChequeStatus.DEPOSITED) {

                throw new RuntimeException(
                        "Only a received or deposited cheque can be bounced."
                );
            }

            if (request.getBounceReason() == null ||
                    request.getBounceReason().isBlank()) {

                throw new RuntimeException(
                        "Bounce reason is required."
                );
            }

            cheque.setBounceReason(
                    request.getBounceReason().trim()
            );
        }

        /*
         * Don't allow arbitrary status changes.
         */
        if (newStatus == ChequeStatus.RECEIVED) {

            throw new RuntimeException(
                    "A cheque cannot be changed back to RECEIVED."
            );
        }

        cheque.setStatus(newStatus);

        if (request.getNotes() != null) {

            cheque.setNotes(
                    request.getNotes().trim()
            );
        }

        return map(
                chequeRepository.save(cheque)
        );
    }

    private ChequeResponse map(Cheque cheque) {

        return ChequeResponse.builder()
                .id(cheque.getId())
                .customerId(cheque.getCustomer().getId())
                .customerName(cheque.getCustomer().getName())
                .amount(cheque.getAmount())
                .chequeNumber(cheque.getChequeNumber())
                .chequeDate(cheque.getChequeDate())
                .receivedDate(cheque.getReceivedDate())
                .depositDate(cheque.getDepositDate())
                .bankName(cheque.getBankName())
                .status(cheque.getStatus().name())
                .bounceReason(cheque.getBounceReason())
                .notes(cheque.getNotes())
                .build();
    }
}