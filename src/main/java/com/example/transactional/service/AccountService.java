package com.example.transactional.service;

import static com.example.transactional.util.SleepUtils.sleep;

import com.example.transactional.entity.Account;
import com.example.transactional.repository.AccountRepository;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository repository;

    // 격리 수준 : READ_UNCOMMITED
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void withdrawReadUnCommitted(Long accountId, Long amount) {
        executeWithdraw(accountId, amount);
    }

    // 격리 수준 : READ_COMMITTED
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdrawReadCommitted(Long accountId, Long amount) {
        executeWithdraw(accountId, amount);
    }

    private void executeWithdraw(Long accountId, Long amount) {
        // 1. 게좌 조회
        Account account = repository.findById(accountId)
            .orElseThrow(() -> new NoSuchElementException("계좌를 찾을 수 없습니다. ID : " + accountId));
        Long currentBalance = account.getBalance();

        // 2. 읽은 잔액 출력
        log.info(Thread.currentThread().getName() + " 현재 잔액 : " + currentBalance);

        // 3. 잔액을 차감한 후 저장
        account.setBalance(currentBalance - amount);

        // 4. 출금 후 잔액 출력
        log.info(Thread.currentThread().getName() + " 출금 후 잔액 : " + account.getBalance());

        sleep(1000); // 트랜잭션 커밋을 지연시킨다.
    }

}
