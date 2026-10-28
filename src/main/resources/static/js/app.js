document.addEventListener('DOMContentLoaded', function () {
    // Toast: tự đóng sau 5 giây (giữ lại nếu người dùng đang rê chuột vào)
    document.querySelectorAll('.rj-toast-stack').forEach(function (stack) {
        var timer = setTimeout(function () { stack.remove(); }, 5000);
        stack.addEventListener('mouseenter', function () { clearTimeout(timer); });
        stack.addEventListener('mouseleave', function () {
            timer = setTimeout(function () { stack.remove(); }, 2500);
        });
    });

    // Bootstrap toast cũ (nếu còn)
    document.querySelectorAll('.toast.show').forEach(function (el) {
        if (window.bootstrap && bootstrap.Toast) {
            new bootstrap.Toast(el, { delay: 3500 }).show();
        }
    });

    // Ô số có nút − / + ([data-rj-stepper]): giữ giá trị trong khoảng min..max
    document.querySelectorAll('[data-rj-stepper]').forEach(function (stepper) {
        var input = stepper.querySelector('input');
        if (!input) {
            return;
        }
        var buttons = stepper.querySelectorAll('[data-step]');

        function bounds() {
            var min = Number(input.min || 1);
            var max = Number(input.max || 99);
            return { min: min, max: max };
        }

        function clamp(value) {
            var b = bounds();
            var n = parseInt(value, 10);
            if (isNaN(n)) {
                n = b.min;
            }
            return Math.min(b.max, Math.max(b.min, n));
        }

        function refresh() {
            var b = bounds();
            var n = clamp(input.value);
            buttons.forEach(function (btn) {
                var step = Number(btn.dataset.step);
                btn.disabled = (step < 0 && n <= b.min) || (step > 0 && n >= b.max);
            });
        }

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var next = clamp(clamp(input.value) + Number(btn.dataset.step));
                if (String(next) !== input.value) {
                    input.value = next;
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }
                refresh();
            });
        });

        input.addEventListener('change', function () {
            var fixed = clamp(input.value);
            if (String(fixed) !== input.value) {
                input.value = fixed;
            }
            refresh();
        });
        input.addEventListener('input', refresh);

        // cho code khác (vd. trang chọn ghế) cập nhật lại trạng thái nút sau khi tự đổi min/giá trị
        input.addEventListener('rj:refresh', refresh);
        refresh();
    });

    // Admin: mở / đóng sidebar trên màn hình nhỏ
    var toggles = document.querySelectorAll('[data-rj-sidebar-toggle]');
    function setSidebar(open) {
        document.body.classList.toggle('rj-sidebar-open', open);
        toggles.forEach(function (btn) { btn.setAttribute('aria-expanded', open ? 'true' : 'false'); });
    }
    toggles.forEach(function (btn) {
        btn.addEventListener('click', function () {
            setSidebar(!document.body.classList.contains('rj-sidebar-open'));
        });
    });
    document.querySelectorAll('[data-rj-sidebar-close]').forEach(function (el) {
        el.addEventListener('click', function () { setSidebar(false); });
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && document.body.classList.contains('rj-sidebar-open')) {
            setSidebar(false);
        }
    });
});

/* ---------------------------------------------------------------------
   Ô ngày / ngày giờ: luôn hiển thị dd/mm/yyyy (+ giờ, thứ) thay cho định dạng của trình duyệt
   (Chrome tiếng Anh hiện mm/dd/yyyy). Input type="date" gốc vẫn giữ nguyên
   name / id / value ISO để gửi form; chỉ phần chữ hiển thị được thay.
   --------------------------------------------------------------------- */
(function () {
    var lang = (window.RAILJET_LANG || document.documentElement.lang || 'vi') === 'en' ? 'en-GB' : 'vi-VN';
    var weekday = new Intl.DateTimeFormat(lang, { weekday: 'long' });
    var t = function (key, fallback) {
        return typeof window.railT === 'function' ? window.railT(key, fallback) : fallback;
    };
    var nativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');

    // "2026-10-26" hoặc "2026-10-26T06:00(:ss)"
    function parseIso(iso) {
        var m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(iso || '');
        return m ? { y: m[1], m: m[2], d: m[3], time: m[4] ? m[4] + ':' + m[5] : null,
                     date: new Date(+m[1], +m[2] - 1, +m[3]) } : null;
    }

    function enhance(input) {
        if (input.dataset.rjDate) {
            return;
        }
        input.dataset.rjDate = '1';

        var withTime = input.type === 'datetime-local';
        var showWeekday = !/birth/i.test(input.name || input.id || '');
        var wrap = document.createElement('div');
        wrap.className = 'rj-date';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);

        var face = document.createElement('span');
        face.className = 'rj-date-face';
        face.setAttribute('aria-hidden', 'true');
        var main = document.createElement('strong');
        var sub = document.createElement('small');
        face.appendChild(main);
        face.appendChild(sub);
        wrap.appendChild(face);

        var icon = document.createElement('span');
        icon.className = 'rj-date-icon';
        icon.setAttribute('aria-hidden', 'true');
        icon.innerHTML = '<i class="bi bi-calendar3"></i>';
        wrap.appendChild(icon);

        if (input.classList.contains('form-control-sm')) {
            wrap.classList.add('is-sm');
        }

        function render() {
            var p = parseIso(nativeValue.get.call(input));
            wrap.classList.toggle('is-empty', !p);
            if (!p) {
                main.textContent = withTime
                    ? t('js.dateTimePlaceholder', 'dd/mm/yyyy --:--')
                    : t('js.datePlaceholder', 'dd/mm/yyyy');
                sub.textContent = '';
                return;
            }
            main.textContent = p.d + '/' + p.m + '/' + p.y + (withTime && p.time ? ' ' + p.time : '');
            sub.textContent = showWeekday ? weekday.format(p.date) : '';
        }

        // Script khác gán input.value = ... cũng cập nhật phần hiển thị.
        Object.defineProperty(input, 'value', {
            configurable: true,
            get: function () { return nativeValue.get.call(input); },
            set: function (v) { nativeValue.set.call(input, v); render(); }
        });

        input.addEventListener('input', render);
        input.addEventListener('change', render);
        if (input.form) {
            // form.reset() không bắn sự kiện input -> vẽ lại sau khi trình duyệt khôi phục giá trị.
            input.form.addEventListener('reset', function () { setTimeout(render, 0); });
        }
        input.addEventListener('click', function () {
            if (typeof input.showPicker === 'function' && !input.disabled && !input.readOnly) {
                try { input.showPicker(); } catch (e) { /* trình duyệt không cho mở: bỏ qua */ }
            }
        });
        if (!input.getAttribute('title')) {
            input.setAttribute('title', t('js.chooseDate', 'Choose a date'));
        }
        render();
    }

    function init(root) {
        (root || document).querySelectorAll('input[type="date"], input[type="datetime-local"]').forEach(enhance);
    }

    window.rjEnhanceDates = init;
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { init(); });
    } else {
        init();
    }
})();

/* Trang kết quả: dải 7 ngày, đánh dấu ngày có / không có chuyến cho cặp ga đang xem. */
document.addEventListener('DOMContentLoaded', function () {
    var strip = document.querySelector('[data-rj-datestrip]');
    if (!strip || !strip.dataset.endpoint || !window.fetch) {
        return;
    }
    var t = function (key, fallback) {
        return typeof window.railT === 'function' ? window.railT(key, fallback) : fallback;
    };
    var params = new URLSearchParams({
        departureStationId: strip.dataset.departure,
        arrivalStationId: strip.dataset.arrival
    });
    fetch(strip.dataset.endpoint + '?' + params.toString(), { headers: { Accept: 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (data) {
            if (!data || !Array.isArray(data.availableDates)) {
                return;
            }
            var available = new Set(data.availableDates);
            strip.querySelectorAll('[data-date]').forEach(function (day) {
                var has = available.has(day.dataset.date);
                day.classList.add(has ? 'has-trips' : 'no-trips');
                day.setAttribute('title', has ? t('js.hasTrips', 'Trains available') : t('js.noTrips', 'No trains'));
            });
            strip.classList.add('is-ready');
        })
        .catch(function () { /* không có dữ liệu: dải ngày vẫn bấm được bình thường */ });
});

/* ---------------------------------------------------------------------
   Sửa trực tiếp trong trang chi tiết.
   [data-inline-edit] (có id) chứa phần xem (.rj-inline-view) và form (.rj-inline-form);
   nút [data-inline-edit-open="<id>"] mở form tại chỗ, [data-inline-edit-cancel] hoặc Esc đóng lại.
   Nút mở vẫn là link (?edit=true) nên trang dùng được cả khi không có JavaScript.
   --------------------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-inline-edit]').forEach(function (box) {
        var form = box.querySelector('form');
        var openers = box.id ? document.querySelectorAll('[data-inline-edit-open="' + box.id + '"]') : [];

        function setEditing(on) {
            box.classList.toggle('is-editing', on);
            Array.prototype.forEach.call(openers, function (btn) {
                btn.classList.toggle('d-none', on);
                btn.setAttribute('aria-expanded', on ? 'true' : 'false');
            });
            if (on) {
                var first = form && form.querySelector('select, input:not([type="hidden"]), textarea');
                if (first) {
                    first.focus({ preventScroll: true });
                }
                box.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            }
        }

        function cancel() {
            if (form) {
                form.reset();
            }
            var alert = box.querySelector('.alert-danger');
            if (alert) {
                alert.remove();
            }
            setEditing(false);
            if (openers.length) {
                openers[0].focus();
            }
        }

        Array.prototype.forEach.call(openers, function (btn) {
            btn.addEventListener('click', function (event) {
                event.preventDefault();
                setEditing(true);
            });
        });

        box.querySelectorAll('[data-inline-edit-cancel]').forEach(function (btn) {
            btn.addEventListener('click', function (event) {
                event.preventDefault();
                cancel();
            });
        });

        box.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && box.classList.contains('is-editing')) {
                cancel();
            }
        });

        if (box.classList.contains('is-editing')) {
            setEditing(true);
        }
    });
});
