package com.rabbittick.persister.domain.orderbook;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderBookServiceImpl implements OrderBookService {

	private static final Logger log = LoggerFactory.getLogger(OrderBookServiceImpl.class);
	private static final long LOG_INTERVAL = 100;

	private final OrderBookRepository orderBookRepository;
	private final OrderBookMapper orderBookMapper;
	private final MeterRegistry meterRegistry;

	@PersistenceContext
	private EntityManager em;

	private final AtomicLong batchCount = new AtomicLong(0);

	@Override
	@Transactional
	public void saveOrderBook(MarketDataMessage<OrderBookPayload> message) {
		OrderBook orderBook = orderBookMapper.toEntity(message);
		orderBookRepository.save(orderBook);
	}

	@Override
	@Transactional
	public void saveOrderBookBatch(String exchange, List<OrderBookPayload> payloads) {
		long t0 = System.nanoTime();

		List<OrderBook> entities = payloads.stream()
			.map(payload -> orderBookMapper.toEntity(exchange, payload))
			.collect(Collectors.toList());

		List<OrderBookUnit> allUnits = entities.stream()
			.flatMap(ob -> ob.getOrderbookUnits().stream())
			.collect(Collectors.toList());
		entities.forEach(ob -> ob.getOrderbookUnits().clear());

		// 1. OrderBook persist → SEQUENCE 채번, id 확보
		orderBookRepository.saveAll(entities);
		long t1 = System.nanoTime();

		// 2. OrderBookUnit persist → order_inserts + batch_size로 배치 flush
		allUnits.forEach(em::persist);
		long t2 = System.nanoTime();

		// 3. flush 명시 → 실제 INSERT 실행 시간 측정
		em.flush();
		long t3 = System.nanoTime();

		long saveAllNs  = t1 - t0;
		long persistNs  = t2 - t1;
		long flushNs    = t3 - t2;
		long totalNs    = t3 - t0;

		Timer.builder("orderbook.batch.saveall")
			.tag("exchange", exchange)
			.description("saveAll() 소요 시간")
			.register(meterRegistry)
			.record(saveAllNs, TimeUnit.NANOSECONDS);

		Timer.builder("orderbook.batch.persist")
			.tag("exchange", exchange)
			.description("em.persist() 루프 소요 시간")
			.register(meterRegistry)
			.record(persistNs, TimeUnit.NANOSECONDS);

		Timer.builder("orderbook.batch.flush")
			.tag("exchange", exchange)
			.description("em.flush() 소요 시간")
			.register(meterRegistry)
			.record(flushNs, TimeUnit.NANOSECONDS);

		Timer.builder("orderbook.batch.total")
			.tag("exchange", exchange)
			.description("배치 전체 소요 시간")
			.register(meterRegistry)
			.record(totalNs, TimeUnit.NANOSECONDS);

		if (batchCount.incrementAndGet() % LOG_INTERVAL == 0) {
			log.info("[진단 요약] exchange={} 누적배치={} | saveAll={}ms persist={}ms flush={}ms total={}ms | orderbook={}건 unit={}건",
				exchange, batchCount.get(),
				saveAllNs  / 1_000_000,
				persistNs  / 1_000_000,
				flushNs    / 1_000_000,
				totalNs    / 1_000_000,
				entities.size(), allUnits.size());
		}
	}
}
