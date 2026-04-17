package com.rabbittick.persister.domain.orderbook;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * 호가 저장 도메인 서비스 구현체.
 *
 * 주요 책임:
 *
 * 메시지 -> 엔티티 변환 호출
 * 트랜잭션 내 저장 처리
 */
@Service
@RequiredArgsConstructor
public class OrderBookServiceImpl implements OrderBookService {

	private final OrderBookRepository orderBookRepository;
	private final OrderBookMapper orderBookMapper;

	@PersistenceContext
	private EntityManager em;

	/**
	 * 호가 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	@Override
	@Transactional
	public void saveOrderBook(MarketDataMessage<OrderBookPayload> message) {
		OrderBook orderBook = orderBookMapper.toEntity(message);
		orderBookRepository.save(orderBook);
	}

	/**
	 * 호가 메시지 목록을 배치 INSERT한다.
	 *
	 * cascade interleave를 방지하기 위해 OrderBookUnit을 부모 컬렉션에서 분리한 뒤
	 * OrderBook을 먼저 flush하여 id를 확보한다.
	 * 이후 OrderBookUnit을 직접 persist하면 order_inserts + batch_size 설정에 의해
	 * 같은 타입 INSERT가 연속으로 배치 flush된다.
	 *
	 * @param exchange 거래소 이름
	 * @param payloads 호가 페이로드 목록
	 */
	@Override
	@Transactional
	public void saveOrderBookBatch(String exchange, List<OrderBookPayload> payloads) {
		List<OrderBook> entities = payloads.stream()
			.map(payload -> orderBookMapper.toEntity(exchange, payload))
			.collect(Collectors.toList());

		// cascade 방지: units를 분리 보관 후 컬렉션 비움
		List<OrderBookUnit> allUnits = entities.stream()
			.flatMap(ob -> ob.getOrderbookUnits().stream())
			.collect(Collectors.toList());
		entities.forEach(ob -> ob.getOrderbookUnits().clear());

		// 1. OrderBook만 persist → SEQUENCE 채번, id 확보
		orderBookRepository.saveAll(entities);

		// 2. OrderBookUnit 전체를 순차 persist → order_inserts + batch_size로 배치 flush
		allUnits.forEach(em::persist);
	}
}
