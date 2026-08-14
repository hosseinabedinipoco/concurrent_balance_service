package com.example.concurrentbalanceservice;

import com.example.concurrentbalanceservice.dto.TransactionRequestDto;
import com.example.concurrentbalanceservice.exception.DuplicateTransactionException;
import com.example.concurrentbalanceservice.exception.InsufficientBalanceException;
import com.example.concurrentbalanceservice.exception.TransactionBadRequestException;
import com.example.concurrentbalanceservice.model.Account;
import com.example.concurrentbalanceservice.model.Transaction;
import com.example.concurrentbalanceservice.model.TransactionStatus;
import com.example.concurrentbalanceservice.model.TransactionType;
import com.example.concurrentbalanceservice.repository.AccountRepository;
import com.example.concurrentbalanceservice.repository.TransactionRepository;
import com.example.concurrentbalanceservice.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConcurrentBalanceServiceApplicationTests {

	@Autowired
	private TransactionService transactionService;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private AccountRepository accountRepository;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAllInBatch();
		accountRepository.deleteAllInBatch();
	}

	private Account createAccount(BigDecimal balance) {

		Account account = Account.builder()
				.username("user-" + UUID.randomUUID())
				.password("password")
				.balance(balance)
				.build();

		return accountRepository.saveAndFlush(account);
	}

	private TransactionRequestDto request(
			UUID transactionUid,
			TransactionType type,
			Long sourceAccountId,
			Long destinationAccountId,
			BigDecimal amount
	) {
		TransactionRequestDto dto = new TransactionRequestDto();

		dto.setTransactionId(transactionUid);
		dto.setTransactionType(type);
		dto.setSourceAccountId(sourceAccountId);
		dto.setDestinationAccountId(destinationAccountId);
		dto.setAmount(amount);

		return dto;
	}

	@Test
	void shouldRejectTransactionWhenTransactionUidIsNull() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				null,
				TransactionType.CREDIT,
				null,
				destination.getId(),
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);

		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void shouldRejectTransactionWhenTypeIsNull() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				null,
				null,
				destination.getId(),
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);

		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void shouldRejectTransactionWhenAmountIsNull() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.CREDIT,
				null,
				destination.getId(),
				null
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectTransactionWhenAmountIsZero() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.CREDIT,
				null,
				destination.getId(),
				BigDecimal.ZERO
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectTransactionWhenAmountIsNegative() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.CREDIT,
				null,
				destination.getId(),
				BigDecimal.valueOf(-10)
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectCreditWithoutDestinationAccount() {

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.CREDIT,
				null,
				null,
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectDebitWithoutSourceAccount() {

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.DEBIT,
				null,
				null,
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectTransferWithoutSourceAccount() {

		Account destination = createAccount(BigDecimal.ZERO);

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.TRANSFER,
				null,
				destination.getId(),
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectTransferWithoutDestinationAccount() {

		Account source = createAccount(BigDecimal.valueOf(100));

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.TRANSFER,
				source.getId(),
				null,
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldRejectTransferToSameAccount() {

		Account account = createAccount(BigDecimal.valueOf(100));

		TransactionRequestDto request = request(
				UUID.randomUUID(),
				TransactionType.TRANSFER,
				account.getId(),
				account.getId(),
				BigDecimal.TEN
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(TransactionBadRequestException.class);
	}

	@Test
	void shouldCreditAccountSuccessfully() {

		Account account = createAccount(BigDecimal.valueOf(100));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.CREDIT,
				null,
				account.getId(),
				BigDecimal.valueOf(50)
		);

		transactionService.createTransaction(request);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("150");

		Transaction transaction =
				transactionRepository.findByTransactionUid(uid).orElseThrow();

		assertThat(transaction.getStatus())
				.isEqualTo(TransactionStatus.SUCCESS);
	}

	@Test
	void shouldDebitAccountSuccessfully() {

		Account account = createAccount(BigDecimal.valueOf(100));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.DEBIT,
				account.getId(),
				null,
				BigDecimal.valueOf(40)
		);

		transactionService.createTransaction(request);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("60");

		Transaction transaction =
				transactionRepository.findByTransactionUid(uid).orElseThrow();

		assertThat(transaction.getStatus())
				.isEqualTo(TransactionStatus.SUCCESS);
	}

	@Test
	void shouldFailDebitWhenBalanceIsInsufficient() {

		Account account = createAccount(BigDecimal.valueOf(100));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.DEBIT,
				account.getId(),
				null,
				BigDecimal.valueOf(150)
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(InsufficientBalanceException.class);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("100");

		Transaction transaction =
				transactionRepository.findByTransactionUid(uid).orElseThrow();

		assertThat(transaction.getStatus())
				.isEqualTo(TransactionStatus.FAILED);
	}

	@Test
	void shouldTransferSuccessfully() {

		Account source =
				createAccount(BigDecimal.valueOf(100));

		Account destination =
				createAccount(BigDecimal.valueOf(50));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.TRANSFER,
				source.getId(),
				destination.getId(),
				BigDecimal.valueOf(30)
		);

		transactionService.createTransaction(request);

		Account updatedSource =
				accountRepository.findById(source.getId()).orElseThrow();

		Account updatedDestination =
				accountRepository.findById(destination.getId()).orElseThrow();

		assertThat(updatedSource.getBalance())
				.isEqualByComparingTo("70");

		assertThat(updatedDestination.getBalance())
				.isEqualByComparingTo("80");

		Transaction transaction =
				transactionRepository.findByTransactionUid(uid).orElseThrow();

		assertThat(transaction.getStatus())
				.isEqualTo(TransactionStatus.SUCCESS);
	}

	@Test
	void shouldFailTransferWhenSourceBalanceIsInsufficient() {

		Account source =
				createAccount(BigDecimal.valueOf(20));

		Account destination =
				createAccount(BigDecimal.valueOf(50));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.TRANSFER,
				source.getId(),
				destination.getId(),
				BigDecimal.valueOf(30)
		);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(InsufficientBalanceException.class);

		Account updatedSource =
				accountRepository.findById(source.getId()).orElseThrow();

		Account updatedDestination =
				accountRepository.findById(destination.getId()).orElseThrow();

		assertThat(updatedSource.getBalance())
				.isEqualByComparingTo("20");

		assertThat(updatedDestination.getBalance())
				.isEqualByComparingTo("50");

		Transaction transaction =
				transactionRepository.findByTransactionUid(uid).orElseThrow();

		assertThat(transaction.getStatus())
				.isEqualTo(TransactionStatus.FAILED);
	}

	@Test
	void shouldNotProcessSameTransactionTwice() {

		Account account =
				createAccount(BigDecimal.valueOf(100));

		UUID uid = UUID.randomUUID();

		TransactionRequestDto request = request(
				uid,
				TransactionType.CREDIT,
				null,
				account.getId(),
				BigDecimal.valueOf(50)
		);

		transactionService.createTransaction(request);

		assertThatThrownBy(() ->
				transactionService.createTransaction(request)
		)
				.isInstanceOf(DuplicateTransactionException.class);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("150");

		assertThat(transactionRepository.count())
				.isEqualTo(1);
	}

	@Test
	void shouldHandleConcurrentDebitsCorrectly() throws Exception {

		Account account =
				createAccount(BigDecimal.valueOf(100));

		ExecutorService executor =
				Executors.newFixedThreadPool(2);

		CountDownLatch startLatch =
				new CountDownLatch(1);

		Callable<Boolean> debitTask = () -> {

			startLatch.await();

			UUID uid = UUID.randomUUID();

			TransactionRequestDto request = request(
					uid,
					TransactionType.DEBIT,
					account.getId(),
					null,
					BigDecimal.valueOf(80)
			);

			try {
				transactionService.createTransaction(request);
				return true;
			} catch (InsufficientBalanceException ex) {
				return false;
			}
		};

		Future<Boolean> first =
				executor.submit(debitTask);

		Future<Boolean> second =
				executor.submit(debitTask);

		startLatch.countDown();

		boolean firstResult = first.get();
		boolean secondResult = second.get();

		executor.shutdown();

		assertThat(firstResult)
				.isNotEqualTo(secondResult);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("20");
	}

	@Test
	void shouldHandleConcurrentOppositeTransfersWithoutDeadlock()
			throws Exception {

		Account accountA =
				createAccount(BigDecimal.valueOf(100));

		Account accountB =
				createAccount(BigDecimal.valueOf(100));

		ExecutorService executor =
				Executors.newFixedThreadPool(2);

		CountDownLatch startLatch =
				new CountDownLatch(1);

		Callable<Void> transferAToB = () -> {

			startLatch.await();

			transactionService.createTransaction(
					request(
							UUID.randomUUID(),
							TransactionType.TRANSFER,
							accountA.getId(),
							accountB.getId(),
							BigDecimal.valueOf(80)
					)
			);

			return null;
		};

		Callable<Void> transferBToA = () -> {

			startLatch.await();

			transactionService.createTransaction(
					request(
							UUID.randomUUID(),
							TransactionType.TRANSFER,
							accountB.getId(),
							accountA.getId(),
							BigDecimal.valueOf(80)
					)
			);

			return null;
		};

		Future<Void> first =
				executor.submit(transferAToB);

		Future<Void> second =
				executor.submit(transferBToA);

		startLatch.countDown();

		first.get(10, TimeUnit.SECONDS);
		second.get(10, TimeUnit.SECONDS);

		executor.shutdown();

		Account updatedA =
				accountRepository.findById(accountA.getId()).orElseThrow();

		Account updatedB =
				accountRepository.findById(accountB.getId()).orElseThrow();

		assertThat(updatedA.getBalance())
				.isEqualByComparingTo("100");

		assertThat(updatedB.getBalance())
				.isEqualByComparingTo("100");
	}

	@Test
	void shouldProcessSameIdempotencyKeyOnlyOnce()
			throws Exception {

		Account account =
				createAccount(BigDecimal.ZERO);

		UUID sameUid = UUID.randomUUID();

		TransactionRequestDto request = request(
				sameUid,
				TransactionType.CREDIT,
				null,
				account.getId(),
				BigDecimal.valueOf(100)
		);

		ExecutorService executor =
				Executors.newFixedThreadPool(2);

		CountDownLatch startLatch =
				new CountDownLatch(1);

		Callable<Boolean> task = () -> {

			startLatch.await();

			try {
				transactionService.createTransaction(request);
				return true;
			} catch (DuplicateTransactionException ex) {
				return false;
			}
		};

		Future<Boolean> first =
				executor.submit(task);

		Future<Boolean> second =
				executor.submit(task);

		startLatch.countDown();

		boolean firstResult = first.get(10, TimeUnit.SECONDS);
		boolean secondResult = second.get(10, TimeUnit.SECONDS);

		executor.shutdown();

		assertThat(firstResult)
				.isNotEqualTo(secondResult);

		Account updated =
				accountRepository.findById(account.getId()).orElseThrow();

		assertThat(updated.getBalance())
				.isEqualByComparingTo("100");

		assertThat(
				transactionRepository.findAll()
		).hasSize(1);
	}

	@Test
	void shouldAllowConcurrentOperationsOnDifferentAccounts()
			throws Exception {

		Account accountA =
				createAccount(BigDecimal.valueOf(100));

		Account accountB =
				createAccount(BigDecimal.valueOf(100));

		ExecutorService executor =
				Executors.newFixedThreadPool(2);

		CountDownLatch startLatch =
				new CountDownLatch(1);

		Callable<Void> debitA = () -> {

			startLatch.await();

			transactionService.createTransaction(
					request(
							UUID.randomUUID(),
							TransactionType.DEBIT,
							accountA.getId(),
							null,
							BigDecimal.valueOf(50)
					)
			);

			return null;
		};

		Callable<Void> debitB = () -> {

			startLatch.await();

			transactionService.createTransaction(
					request(
							UUID.randomUUID(),
							TransactionType.DEBIT,
							accountB.getId(),
							null,
							BigDecimal.valueOf(50)
					)
			);

			return null;
		};

		Future<Void> first =
				executor.submit(debitA);

		Future<Void> second =
				executor.submit(debitB);

		startLatch.countDown();

		first.get(10, TimeUnit.SECONDS);
		second.get(10, TimeUnit.SECONDS);

		executor.shutdown();

		Account updatedA =
				accountRepository.findById(accountA.getId()).orElseThrow();

		Account updatedB =
				accountRepository.findById(accountB.getId()).orElseThrow();

		assertThat(updatedA.getBalance())
				.isEqualByComparingTo("50");

		assertThat(updatedB.getBalance())
				.isEqualByComparingTo("50");
	}
}
