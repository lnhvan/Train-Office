/*
 * Đồng hồ giữ ghế (fragments/hold-timer.html).
 * - Thời gian ban đầu do server đọc từ TTL Redis (data-remaining-ms) -> tải lại trang vẫn đúng.
 * - Mỗi 30 giây (và khi quay lại tab) hỏi lại server để khớp với Redis
 *   (vd. khách mở nhiều tab, admin hủy chuyến).
 * - Hết giờ: hiện popup rồi tự chuyển về trang tìm vé.
 * - Trang chọn ghế gọi window.RailHoldTimer.start(ms) sau mỗi lần giữ / bỏ ghế; bỏ hết ghế đồng hồ vẫn chạy theo hạn cũ.
 */
(function () {
    'use strict';

    const SYNC_INTERVAL_MS = 30000;
    const REDIRECT_AFTER_SECONDS = 10;
    const WARNING_MS = 2 * 60 * 1000;
    const CRITICAL_MS = 30 * 1000;

    function t(key, fallback) {
        const args = Array.prototype.slice.call(arguments, 2);
        if (typeof window.railT === 'function') {
            return window.railT.apply(null, [key, fallback].concat(args));
        }
        return String(fallback).replace(/\{(\d+)\}/g, function (match, index) {
            return args[index] !== undefined ? args[index] : match;
        });
    }

    function init() {
        const el = document.getElementById('rjHoldTimer');
        if (!el) {
            return;
        }

        const clock = el.querySelector('[data-hold-clock]');
        const syncUrl = el.dataset.syncUrl || '';
        const expiredUrl = el.dataset.expiredUrl || '/booking/search';

        let deadline = null;
        let tickId = null;
        let expired = false;

        function format(ms) {
            const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
            const minutes = Math.floor(totalSeconds / 60);
            const seconds = totalSeconds % 60;
            return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
        }

        function tick() {
            if (deadline === null) {
                return;
            }
            const left = deadline - Date.now();
            if (left <= 0) {
                clock.textContent = '00:00';
                onExpired();
                return;
            }
            clock.textContent = format(left);
            el.classList.toggle('is-warning', left <= WARNING_MS && left > CRITICAL_MS);
            el.classList.toggle('is-critical', left <= CRITICAL_MS);
        }

        function start(remainingMs) {
            const ms = Number(remainingMs || 0);
            if (expired) {
                return;
            }
            if (!ms || ms <= 0) {
                stop();
                return;
            }
            // Dùng thời gian còn lại do server trả về, không dùng giờ máy khách làm mốc tuyệt đối.
            deadline = Date.now() + ms;
            el.hidden = false;
            tick();
            if (tickId === null) {
                tickId = window.setInterval(tick, 1000);
            }
        }

        function stop() {
            if (tickId !== null) {
                window.clearInterval(tickId);
                tickId = null;
            }
            deadline = null;
            el.hidden = true;
            el.classList.remove('is-warning', 'is-critical');
        }

        function redirect() {
            window.location.href = expiredUrl;
        }

        function onExpired() {
            if (expired) {
                return;
            }
            expired = true;
            if (tickId !== null) {
                window.clearInterval(tickId);
                tickId = null;
            }

            // Không cho thao tác tiếp trên trang (gửi form với ghế đã nhả).
            document.querySelectorAll('form button[type="submit"], form input[type="submit"]').forEach(function (button) {
                button.disabled = true;
            });

            const modalEl = document.getElementById('rjHoldExpiredModal');
            const countdownText = modalEl ? modalEl.querySelector('[data-hold-redirect-text]') : null;
            let secondsLeft = REDIRECT_AFTER_SECONDS;

            function updateCountdown() {
                if (countdownText) {
                    countdownText.textContent = t('js.holdRedirectIn', 'Redirecting to ticket search in {0}s…', secondsLeft);
                }
            }

            if (modalEl && window.bootstrap && window.bootstrap.Modal) {
                updateCountdown();
                window.bootstrap.Modal.getOrCreateInstance(modalEl).show();
                const countdownId = window.setInterval(function () {
                    secondsLeft -= 1;
                    if (secondsLeft <= 0) {
                        window.clearInterval(countdownId);
                        redirect();
                        return;
                    }
                    updateCountdown();
                }, 1000);
            } else {
                window.alert(t('js.holdExpired', 'Your seat hold has expired. Please search for tickets again.'));
                redirect();
            }
        }

        function sync() {
            if (!syncUrl || expired) {
                return;
            }
            fetch(syncUrl, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
                .then(function (response) {
                    if (response.status === 401) {
                        return null;
                    }
                    return response.ok ? response.json() : null;
                })
                .then(function (data) {
                    if (!data) {
                        return;
                    }
                    if (data.active && data.remainingMs > 0) {
                        start(data.remainingMs);
                    } else if (deadline !== null) {
                        // Đồng hồ đang chạy mà Redis báo đã hết giữ chỗ.
                        onExpired();
                    }
                })
                .catch(function () {
                    // Mất mạng tạm thời: tiếp tục đếm theo lần đồng bộ trước.
                });
        }

        window.RailHoldTimer = {
            start: start,
            stop: stop,
            expire: onExpired,
            isRunning: function () {
                return deadline !== null;
            }
        };

        start(el.dataset.remainingMs);

        if (syncUrl) {
            window.setInterval(sync, SYNC_INTERVAL_MS);
            document.addEventListener('visibilitychange', function () {
                if (document.visibilityState === 'visible') {
                    sync();
                }
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
