package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.BookingHoldPolicy;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.exception.SeatHoldException.Reason;
import com.hsf302.trainoffice.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Giữ ghế bằng Redis. Xem mô tả key ở {@link SeatHoldService}.
 *
 * <ul>
 *     <li>{@code rj:seat:{t<tripId>}:<seatId>:<leg>} – khóa một đoạn của một ghế, value = chủ khóa, có TTL.
 *     Phần {@code {t<tripId>}} là hash tag: mọi khóa của một chuyến nằm cùng slot nên script Lua
 *     thao tác nhiều khóa một lúc vẫn chạy được trên Redis Cluster.</li>
 *     <li>{@code rj:hold:u:<userId>} / {@code rj:hold:b:<bookingId>} – hash mô tả lần giữ chỗ
 *     (chuyến, chặng, danh sách ghế), cùng TTL với các khóa ghế. Thời gian còn lại đọc từ PTTL của hash.</li>
 * </ul>
 */
@Service
public class RedisSeatHoldService implements SeatHoldService {

    private static final Logger log = LoggerFactory.getLogger(RedisSeatHoldService.class);

    private static final String F_TRIP = "trip";
    private static final String F_DEP = "dep";
    private static final String F_ARR = "arr";
    private static final String F_DEP_ORDER = "depOrder";
    private static final String F_ARR_ORDER = "arrOrder";
    private static final String F_SEATS = "seats";
    /** Hạn giữ chỗ gốc của đơn (epoch ms), dùng để trả lại hạn cũ khi thanh toán thất bại. */
    private static final String F_HOLD_UNTIL = "holdUntil";

    /** Khóa tất cả đoạn hoặc không khóa đoạn nào. KEYS = các đoạn; ARGV = chủ khóa, ttl ms. */
    private static final RedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
              local v = redis.call('GET', KEYS[i])
              if v and v ~= ARGV[1] then return 0 end
            end
            for i = 1, #KEYS do
              redis.call('SET', KEYS[i], ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    /** Xóa các đoạn đang thuộc chủ khóa ARGV[1]; đoạn của người khác giữ nguyên. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local n = 0
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) == ARGV[1] then
                redis.call('DEL', KEYS[i])
                n = n + 1
              end
            end
            return n
            """, Long.class);

    /** Đổi chủ khóa ARGV[1] -> ARGV[2], giữ nguyên TTL. Có đoạn không thuộc ARGV[1] thì không đổi gì. */
    private static final RedisScript<Long> TRANSFER = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) ~= ARGV[1] then return 0 end
            end
            for i = 1, #KEYS do
              redis.call('SET', KEYS[i], ARGV[2], 'KEEPTTL')
            end
            return 1
            """, Long.class);

    /** Đặt hạn tuyệt đối ARGV[2] (epoch ms) cho các đoạn, chỉ khi tất cả còn thuộc ARGV[1]. */
    private static final RedisScript<Long> EXPIRE_AT = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) ~= ARGV[1] then return 0 end
            end
            for i = 1, #KEYS do
              redis.call('PEXPIREAT', KEYS[i], ARGV[2])
            end
            return 1
            """, Long.class);

    /** 1 nếu mọi đoạn còn thuộc ARGV[1]. */
    private static final RedisScript<Long> OWNS_ALL = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) ~= ARGV[1] then return 0 end
            end
            return 1
            """, Long.class);

    /** Ghi lại toàn bộ hash và đặt TTL trong một lệnh (không để sót hash không có TTL). */
    private static final RedisScript<Long> WRITE_HOLD = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV, 2 do
              redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final long holdMillis;

    @Autowired
    public RedisSeatHoldService(StringRedisTemplate redis) {
        this(redis, "rj:", TimeUnit.MINUTES.toMillis(BookingHoldPolicy.HOLD_MINUTES));
    }

    /** Dùng cho test: tiền tố key riêng và thời gian giữ chỗ tùy chọn. */
    public RedisSeatHoldService(StringRedisTemplate redis, String keyPrefix, long holdMillis) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.holdMillis = holdMillis;
    }

    // ------------------------------------------------------------------ khách chọn ghế

    @Override
    public SeatHold holdSeat(Long userId,
                             Long tripId,
                             Long departureStationId,
                             Long arrivalStationId,
                             int departureOrder,
                             int arrivalOrder,
                             Long seatId,
                             int maxSeats) {
        requireSegment(departureOrder, arrivalOrder);

        return call(() -> {
            String owner = SeatHoldService.userOwner(userId);
            String holdKey = userHoldKey(userId);
            Stored existing = read(holdKey).orElse(null);

            if (existing != null && !existing.hold.isForSegment(tripId, departureStationId, arrivalStationId)) {
                // Khách chuyển sang chuyến / chặng khác: bỏ giữ chặng cũ.
                releaseLegs(existing.hold, existing.hold.seatIds(), owner);
                redis.delete(holdKey);
                log.info("[SeatHold] user={} switched segment, released trip={} seats={}",
                        userId, existing.hold.tripId(), existing.hold.seatIds());
                existing = null;
            }

            List<Long> seats = existing == null ? new ArrayList<>() : new ArrayList<>(existing.hold.seatIds());
            long ttl = existing == null ? holdMillis : existing.hold.remainingMillis();

            if (seats.contains(seatId)) {
                return existing.hold;
            }

            if (seats.size() >= Math.max(1, maxSeats)) {
                log.info("[SeatHold] user={} trip={} seat={} REJECTED limit={}", userId, tripId, seatId, maxSeats);
                throw new SeatHoldException(Reason.LIMIT_REACHED, I18n.t("err.seatHoldLimitReached", maxSeats));
            }

            List<String> legs = legKeys(tripId, seatId, departureOrder, arrivalOrder);
            Long acquired = redis.execute(ACQUIRE, legs, owner, String.valueOf(ttl));

            if (acquired == null || acquired != 1L) {
                log.info("[SeatHold] user={} trip={} seat={} legs={}-{} REJECTED already held",
                        userId, tripId, seatId, departureOrder, arrivalOrder);
                throw new SeatHoldException(Reason.SEAT_TAKEN, I18n.t("err.seatHeldByAnotherCustomer"));
            }

            seats.add(seatId);
            SeatHold hold = new SeatHold(tripId, departureStationId, arrivalStationId,
                    departureOrder, arrivalOrder, seats, ttl);
            write(holdKey, hold, ttl, null);
            log.info("[SeatHold] user={} trip={} seat={} legs={}-{} HELD seats={} remaining={}s",
                    userId, tripId, seatId, departureOrder, arrivalOrder, seats, hold.remainingSeconds());
            return hold;
        });
    }

    @Override
    public Optional<SeatHold> releaseSeat(Long userId, Long seatId) {
        return call(() -> {
            String holdKey = userHoldKey(userId);
            Optional<Stored> stored = read(holdKey);

            if (stored.isEmpty() || !stored.get().hold.seatIds().contains(seatId)) {
                return stored.map(Stored::hold);
            }

            SeatHold current = stored.get().hold;
            releaseLegs(current, List.of(seatId), SeatHoldService.userOwner(userId));

            List<Long> seats = new ArrayList<>(current.seatIds());
            seats.remove(seatId);

            // Giữ nguyên hạn (kể cả khi không còn ghế) để bỏ chọn rồi chọn lại không được thêm giờ.
            SeatHold updated = new SeatHold(current.tripId(), current.departureStationId(), current.arrivalStationId(),
                    current.departureOrder(), current.arrivalOrder(), seats, current.remainingMillis());
            write(holdKey, updated, current.remainingMillis(), null);
            log.info("[SeatHold] user={} trip={} seat={} RELEASED seats={} remaining={}s",
                    userId, current.tripId(), seatId, seats, updated.remainingSeconds());
            return Optional.of(updated);
        });
    }

    @Override
    public Optional<SeatHold> findUserHold(Long userId) {
        return call(() -> read(userHoldKey(userId)).map(Stored::hold));
    }

    @Override
    public void releaseUserHold(Long userId) {
        call(() -> {
            String holdKey = userHoldKey(userId);
            read(holdKey).ifPresent(stored -> {
                releaseLegs(stored.hold, stored.hold.seatIds(), SeatHoldService.userOwner(userId));
                log.info("[SeatHold] user={} RELEASED ALL trip={} seats={}",
                        userId, stored.hold.tripId(), stored.hold.seatIds());
            });
            redis.delete(holdKey);
            return null;
        });
    }

    @Override
    public SeatHold requireUserHold(Long userId,
                                    Long tripId,
                                    Long departureStationId,
                                    Long arrivalStationId,
                                    Collection<Long> seatIds) {
        return call(() -> {
            SeatHold hold = read(userHoldKey(userId)).map(Stored::hold).orElse(null);

            boolean matches = hold != null
                    && hold.hasSeats()
                    && hold.isForSegment(tripId, departureStationId, arrivalStationId)
                    && seatIds != null
                    && new HashSet<>(hold.seatIds()).equals(new HashSet<>(seatIds))
                    && ownsAll(hold, SeatHoldService.userOwner(userId));

            if (!matches) {
                throw expired();
            }

            return hold;
        });
    }

    // ------------------------------------------------------------------ đơn chờ thanh toán

    @Override
    public SeatHold transferToBooking(Long userId, Long bookingId, LocalDateTime payUntil) {
        long payUntilMillis = toEpochMillis(payUntil);

        return call(() -> {
            String userKey = userHoldKey(userId);
            SeatHold hold = read(userKey).map(Stored::hold).orElse(null);

            if (hold == null || !hold.hasSeats()) {
                throw expired();
            }

            Long moved = redis.execute(TRANSFER, legKeys(hold, hold.seatIds()),
                    SeatHoldService.userOwner(userId), SeatHoldService.bookingOwner(bookingId));

            if (moved == null || moved != 1L) {
                throw expired();
            }

            // Hạn giữ chỗ đặt lại thành thời hạn thanh toán của đơn.
            long ttl = Math.max(1, payUntilMillis - System.currentTimeMillis());
            redis.execute(EXPIRE_AT, legKeys(hold, hold.seatIds()),
                    SeatHoldService.bookingOwner(bookingId), String.valueOf(payUntilMillis));
            SeatHold bookingHold = new SeatHold(hold.tripId(), hold.departureStationId(), hold.arrivalStationId(),
                    hold.departureOrder(), hold.arrivalOrder(), hold.seatIds(), ttl);
            write(bookingHoldKey(bookingId), bookingHold, ttl, payUntilMillis);
            redis.delete(userKey);
            log.info("[SeatHold] user={} -> booking={} TRANSFERRED trip={} seats={} payUntil={}",
                    userId, bookingId, hold.tripId(), hold.seatIds(), payUntil);
            return bookingHold;
        });
    }

    @Override
    public Optional<SeatHold> findBookingHold(Long bookingId) {
        return call(() -> read(bookingHoldKey(bookingId)).map(Stored::hold));
    }

    @Override
    public boolean isBookingHoldActive(Long bookingId) {
        return findBookingHold(bookingId).isPresent();
    }

    @Override
    public boolean extendBookingHold(Long bookingId, LocalDateTime until) {
        long untilMillis = toEpochMillis(until);

        return call(() -> {
            String holdKey = bookingHoldKey(bookingId);
            Stored stored = read(holdKey).orElse(null);

            if (stored == null) {
                return false;
            }

            // Chỉ được bắt đầu thanh toán trước thời hạn thanh toán (không gia hạn mãi bằng cách bấm lại).
            if (stored.holdUntil != null && stored.holdUntil <= System.currentTimeMillis()) {
                log.info("[SeatHold] booking={} payment deadline passed, cannot start payment", bookingId);
                return false;
            }

            SeatHold hold = stored.hold;

            if (untilMillis <= System.currentTimeMillis() + hold.remainingMillis()) {
                return true;
            }

            Long ok = redis.execute(EXPIRE_AT, legKeys(hold, hold.seatIds()),
                    SeatHoldService.bookingOwner(bookingId), String.valueOf(untilMillis));

            if (ok == null || ok != 1L) {
                return false;
            }

            redis.expireAt(holdKey, new Date(untilMillis));
            log.info("[SeatHold] booking={} EXTENDED for payment until={} seats={}",
                    bookingId, until, hold.seatIds());
            return true;
        });
    }

    @Override
    public void restoreBookingHoldAfterFailedPayment(Long bookingId) {
        call(() -> {
            String holdKey = bookingHoldKey(bookingId);
            Stored stored = read(holdKey).orElse(null);

            if (stored == null) {
                return null;
            }

            long now = System.currentTimeMillis();
            Long holdUntil = stored.holdUntil;

            if (holdUntil == null || holdUntil <= now) {
                log.info("[SeatHold] booking={} payment failed, hold already over -> RELEASED seats={}",
                        bookingId, stored.hold.seatIds());
                releaseLegs(stored.hold, stored.hold.seatIds(), SeatHoldService.bookingOwner(bookingId));
                redis.delete(holdKey);
                return null;
            }

            if (now + stored.hold.remainingMillis() > holdUntil) {
                redis.execute(EXPIRE_AT, legKeys(stored.hold, stored.hold.seatIds()),
                        SeatHoldService.bookingOwner(bookingId), String.valueOf(holdUntil));
                redis.expireAt(holdKey, new Date(holdUntil));
            }
            log.info("[SeatHold] booking={} payment failed -> RESTORED remaining={}s",
                    bookingId, (holdUntil - now + 999) / 1000);
            return null;
        });
    }

    @Override
    public void releaseBookingHold(Long bookingId) {
        call(() -> {
            String holdKey = bookingHoldKey(bookingId);
            read(holdKey).ifPresent(stored -> {
                releaseLegs(stored.hold, stored.hold.seatIds(), SeatHoldService.bookingOwner(bookingId));
                log.info("[SeatHold] booking={} RELEASED trip={} seats={}",
                        bookingId, stored.hold.tripId(), stored.hold.seatIds());
            });
            redis.delete(holdKey);
            return null;
        });
    }

    @Override
    public void releaseTrip(Long tripId) {
        call(() -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(keyPrefix + "seat:{t" + tripId + "}:*")
                    .count(500)
                    .build();

            List<String> batch = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= 500) {
                        redis.delete(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
            log.info("[SeatHold] trip={} cancelled -> RELEASED all seat locks", tripId);
            return null;
        });
    }

    @Override
    public Map<Long, String> findSeatOwners(Long tripId,
                                            int departureOrder,
                                            int arrivalOrder,
                                            Collection<Long> seatIds) {
        requireSegment(departureOrder, arrivalOrder);

        if (seatIds == null || seatIds.isEmpty()) {
            return Map.of();
        }

        return call(() -> {
            List<Long> orderedSeats = new ArrayList<>(new LinkedHashSet<>(seatIds));
            int legsPerSeat = arrivalOrder - departureOrder;
            List<String> keys = new ArrayList<>(orderedSeats.size() * legsPerSeat);

            for (Long seatId : orderedSeats) {
                keys.addAll(legKeys(tripId, seatId, departureOrder, arrivalOrder));
            }

            List<String> values = redis.opsForValue().multiGet(keys);
            Map<Long, String> owners = new LinkedHashMap<>();

            if (values == null) {
                return owners;
            }

            for (int i = 0; i < values.size(); i++) {
                String owner = values.get(i);
                if (owner != null) {
                    owners.putIfAbsent(orderedSeats.get(i / legsPerSeat), owner);
                }
            }
            return owners;
        });
    }

    // ------------------------------------------------------------------ helpers

    private record Stored(SeatHold hold, Long holdUntil) {
    }

    private Optional<Stored> read(String holdKey) {
        Map<Object, Object> fields = redis.opsForHash().entries(holdKey);

        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }

        Long ttl = redis.getExpire(holdKey, TimeUnit.MILLISECONDS);

        if (ttl == null || ttl <= 0) {
            // -2: vừa hết hạn; -1: không có TTL (không được xảy ra) -> coi như hết hạn và dọn.
            if (ttl != null && ttl == -1) {
                redis.delete(holdKey);
            }
            return Optional.empty();
        }

        List<Long> seats = new ArrayList<>();
        String seatCsv = string(fields.get(F_SEATS));
        if (!seatCsv.isBlank()) {
            for (String part : seatCsv.split(",")) {
                seats.add(Long.valueOf(part.trim()));
            }
        }

        SeatHold hold = new SeatHold(
                Long.valueOf(string(fields.get(F_TRIP))),
                Long.valueOf(string(fields.get(F_DEP))),
                Long.valueOf(string(fields.get(F_ARR))),
                Integer.parseInt(string(fields.get(F_DEP_ORDER))),
                Integer.parseInt(string(fields.get(F_ARR_ORDER))),
                seats,
                ttl);

        String holdUntil = string(fields.get(F_HOLD_UNTIL));
        return Optional.of(new Stored(hold, holdUntil.isBlank() ? null : Long.valueOf(holdUntil)));
    }

    private void write(String holdKey, SeatHold hold, long ttlMillis, Long holdUntil) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(Math.max(1, ttlMillis)));
        addField(args, F_TRIP, hold.tripId());
        addField(args, F_DEP, hold.departureStationId());
        addField(args, F_ARR, hold.arrivalStationId());
        addField(args, F_DEP_ORDER, hold.departureOrder());
        addField(args, F_ARR_ORDER, hold.arrivalOrder());
        addField(args, F_SEATS, String.join(",", hold.seatIds().stream().map(String::valueOf).toList()));
        if (holdUntil != null) {
            addField(args, F_HOLD_UNTIL, holdUntil);
        }
        redis.execute(WRITE_HOLD, List.of(holdKey), args.toArray());
    }

    private static void addField(List<String> args, String name, Object value) {
        args.add(name);
        args.add(String.valueOf(value));
    }

    private boolean ownsAll(SeatHold hold, String owner) {
        Long ok = redis.execute(OWNS_ALL, legKeys(hold, hold.seatIds()), owner);
        return ok != null && ok == 1L;
    }

    private void releaseLegs(SeatHold hold, Collection<Long> seatIds, String owner) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        redis.execute(RELEASE, legKeys(hold, seatIds), owner);
    }

    private List<String> legKeys(SeatHold hold, Collection<Long> seatIds) {
        List<String> keys = new ArrayList<>();
        for (Long seatId : seatIds) {
            keys.addAll(legKeys(hold.tripId(), seatId, hold.departureOrder(), hold.arrivalOrder()));
        }
        return keys;
    }

    /** Một key cho mỗi đoạn [order, order + 1) nằm trong chặng [departureOrder, arrivalOrder). */
    private List<String> legKeys(Long tripId, Long seatId, int departureOrder, int arrivalOrder) {
        List<String> keys = new ArrayList<>(arrivalOrder - departureOrder);
        for (int leg = departureOrder; leg < arrivalOrder; leg++) {
            keys.add(keyPrefix + "seat:{t" + tripId + "}:" + seatId + ":" + leg);
        }
        return keys;
    }

    private String userHoldKey(Long userId) {
        return keyPrefix + "hold:u:" + Objects.requireNonNull(userId);
    }

    private String bookingHoldKey(Long bookingId) {
        return keyPrefix + "hold:b:" + Objects.requireNonNull(bookingId);
    }

    private static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static void requireSegment(int departureOrder, int arrivalOrder) {
        if (departureOrder >= arrivalOrder) {
            throw new IllegalArgumentException(I18n.t("err.departureStationMustBeBeforeArrivalStation"));
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static SeatHoldException expired() {
        return new SeatHoldException(Reason.EXPIRED, I18n.t("err.seatHoldExpired"));
    }

    /** Lỗi kết nối Redis -> SeatHoldException(UNAVAILABLE); lỗi nghiệp vụ giữ nguyên. */
    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            log.error("[SeatHold] Redis unavailable: {}", ex.getMessage());
            throw new SeatHoldException(Reason.UNAVAILABLE, I18n.t("err.seatHoldUnavailable"), ex);
        }
    }
}
