package dev.silentauth.account;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Validates stored tokens on its own, so nothing has to be checked by hand.
 *
 * <p>The pool is small on purpose: opening the screen with fifty accounts should not fire
 * fifty requests at Mojang at once. Accounts checked in the last few minutes are skipped, so
 * reopening the screen is free.</p>
 */
public final class AccountChecker {

    private static final long FRESH_FOR_MS = 5L * 60L * 1000L;

    private final ExecutorService pool = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SilentAuth-TokenCheck-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    /** Checks everything that has not been checked recently. Returns at once. */
    public void checkAll(List<Account> accounts) {
        for (Account account : accounts) {
            check(account, false);
        }
    }

    /**
     * Queues one account.
     *
     * @param force check even if it was checked a moment ago
     */
    public void check(final Account account, boolean force) {
        if (account == null) {
            return;
        }
        if (account.getValidity() == Validity.CHECKING) {
            return;
        }
        if (!force && account.wasCheckedWithin(FRESH_FOR_MS)) {
            return;
        }
        account.setValidity(Validity.CHECKING, "");
        pool.submit(new Runnable() {
            @Override
            public void run() {
                LoginService.check(account);
            }
        });
    }
}
