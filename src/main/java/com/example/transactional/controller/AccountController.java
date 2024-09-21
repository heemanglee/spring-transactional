package com.example.transactional.controller;

import static com.example.transactional.util.SleepUtils.sleep;

import com.example.transactional.service.AccountService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // 격리 수준 : READ_UNCOMMITTED
    // 상황 : 두 스레드가 DB에 저장된 잔액 100원을 동시에 읽는 상황 -> Race Condition 발생
    @PostMapping("/{accountId}/read-uncommitted/common")
    public void withdraw_ReadUncommitted_Common(@PathVariable("accountId") Long accountId)
        throws InterruptedException {

        /*
         * 2024-09-21T17:01:44.341+09:00  INFO 5276 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 현재 잔액 : 100
         * 2024-09-21T17:01:44.341+09:00  INFO 5276 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 현재 잔액 : 100
         * 2024-09-21T17:01:44.342+09:00  INFO 5276 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 출금 후 잔액 : 50
         * 2024-09-21T17:01:44.342+09:00  INFO 5276 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 출금 후 잔액 : 30
         */

        // 스레드가 작업할 내용 작성
        Runnable task1 = () -> {
            service.withdrawReadUnCommitted(accountId, 50L);
        };
        Runnable task2 = () -> {
            service.withdrawReadUnCommitted(accountId, 70L);
        };

        // 두 스레드 동시에 실행
        executor.submit(task1);
        executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // 격리 수준 : READ_UNCOMMITTED
    // 상황 : 먼저 실행된 스레드가 DB에서 읽은 잔액에서 출금을 시도하고, 다음 스레드가 DB에서 출금된 잔액을 읽는 상황 => Dirty Read 발생
    @PostMapping("/{accountId}/read-uncommitted/dirty-read")
    public void withdraw_ReadUncommitted_DirtyRead(@PathVariable("accountId") Long accountId)
        throws InterruptedException {

        /*
         * 2024-09-21T17:00:21.327+09:00  INFO 5259 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 현재 잔액 : 100
         * 2024-09-21T17:00:21.327+09:00  INFO 5259 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 출금 후 잔액 : 50
         * 2024-09-21T17:00:21.401+09:00  INFO 5259 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 현재 잔액 : 50
         * 2024-09-21T17:00:21.401+09:00  INFO 5259 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 출금 후 잔액 : -20
         */

        Runnable task1 = () -> {
            service.withdrawReadUnCommitted(accountId, 50L);
        };
        Runnable task2 = () -> {
            service.withdrawReadUnCommitted(accountId, 70L);
        };

        executor.submit(task1);

        sleep(100); // Dirty Read를 발생시키기 위해 sleep

        executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // 격리 수준 : READ_COMMITTED
    // 상황 : 두 스레드가 DB에 저장된 잔액 100원을 동시에 읽는 상황 => Race Condition 발생
    @PostMapping("/{accountId}/read-committed/common")
    public void withdraw_ReadCommitted_common(@PathVariable("accountId") Long accountId)
        throws InterruptedException {

        /*
         * 2024-09-21T17:02:23.689+09:00  INFO 5299 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 현재 잔액 : 100
         * 2024-09-21T17:02:23.689+09:00  INFO 5299 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 현재 잔액 : 100
         * 2024-09-21T17:02:23.689+09:00  INFO 5299 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 출금 후 잔액 : 30
         * 2024-09-21T17:02:23.689+09:00  INFO 5299 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 출금 후 잔액 : 50
         */

        Runnable task1 = () -> {
            service.withdrawReadCommitted(accountId, 50L);
        };
        Runnable task2 = () -> {
            service.withdrawReadCommitted(accountId, 70L);
        };

        executor.submit(task1);
        executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // 격리 수준 : READ_COMMITTED
    // 상황 : 먼저 실행된 스레드가 현재 잔액을 수정하고 커밋을 하지 않은 상태에서 다음 스레드가 현재 잔액을 읽으려고 했으나 읽지 못하는 상황 => Dirty Read 방지
    @PostMapping("/{accountId}/read-committed/dirty-read")
    public void withdrawByReadCommitted_prevent_DirtyRead(@PathVariable("accountId") Long accountId)
        throws InterruptedException {

        /**
         * 2024-09-21T17:11:21.860+09:00  INFO 5474 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 현재 잔액 : 100
         * 2024-09-21T17:11:21.860+09:00  INFO 5474 --- [pool-1-thread-1] c.e.t.service.AccountService             : pool-1-thread-1 출금 후 잔액 : 50
         * 2024-09-21T17:11:21.929+09:00  INFO 5474 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 현재 잔액 : 100
         * 2024-09-21T17:11:21.929+09:00  INFO 5474 --- [pool-1-thread-2] c.e.t.service.AccountService             : pool-1-thread-2 출금 후 잔액 : 30
         */

        Runnable task1 = () -> {
            service.withdrawReadCommitted(accountId, 50L);
        };
        Runnable task2 = () -> {
            service.withdrawReadCommitted(accountId, 70L);
        };

        executor.submit(task1);

        // 먼저 실행된 스레드가 출금 후 잔액을 읽을 수 있도록 다음 스레드는 대기
        // Dirty Read가 발생하지 않는 테스트하기 위한 용도
        sleep(100);

        executor.submit(task2);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

}
