document.addEventListener('DOMContentLoaded', function () {
    const oneWay = document.getElementById('oneWay');
    const roundTrip = document.getElementById('roundTrip');
    const returnDateGroup = document.getElementById('returnDateGroup');
    const returnDateInput = document.getElementById('returnDate');
    const departureDateInput = document.getElementById('departureDate');

    if (!oneWay || !roundTrip || !returnDateGroup || !returnDateInput || !departureDateInput) {
        return;
    }

    function toggleReturnDate() {
        if (roundTrip.checked) {
            returnDateGroup.classList.add('show');
            returnDateInput.required = true;

            if (departureDateInput.value) {
                returnDateInput.min = departureDateInput.value;
            }
        } else {
            returnDateGroup.classList.remove('show');
            returnDateInput.required = false;
            returnDateInput.value = '';
        }
    }

    oneWay.addEventListener('change', toggleReturnDate);
    roundTrip.addEventListener('change', toggleReturnDate);

    departureDateInput.addEventListener('change', function () {
        if (departureDateInput.value) {
            returnDateInput.min = departureDateInput.value;
        }
    });

    toggleReturnDate();
});

/* Đổi chiều ga đi / ga đến */
document.addEventListener('DOMContentLoaded', function () {
    const swap = document.getElementById('swapStations');
    const from = document.getElementById('departureStationId');
    const to = document.getElementById('arrivalStationId');

    if (!swap || !from || !to) {
        return;
    }

    swap.addEventListener('click', function () {
        const value = from.value;
        from.value = to.value;
        to.value = value;
        swap.classList.toggle('is-flipped');
    });
});

/*
 * Gợi ý theo dữ liệu chuyến: mỗi khi đổi ga đi / ga đến / ngày (hoặc đổi chiều, bật khứ hồi),
 * gọi /api/booking/station-availability để:
 *   - làm mờ các ga đi không có chuyến (tới ga đến đã chọn, trong ngày đã chọn),
 *   - làm mờ các ga đến không có chuyến (từ ga đi đã chọn, trong ngày đã chọn),
 *   - hiện các ngày có chuyến trong 60 ngày tới cho cặp ga (bấm để chọn),
 *   - báo khi lựa chọn hiện tại không có chuyến.
 */
document.addEventListener('DOMContentLoaded', function () {
    const form = document.querySelector('.rj-searchbar[data-availability-url]');
    const from = document.getElementById('departureStationId');
    const to = document.getElementById('arrivalStationId');
    const date = document.getElementById('departureDate');
    const returnDate = document.getElementById('returnDate');
    const roundTrip = document.getElementById('roundTrip');
    const oneWay = document.getElementById('oneWay');
    const swap = document.getElementById('swapStations');
    const panel = document.getElementById('availabilityPanel');
    const hint = document.getElementById('availabilityHint');
    const departureRow = document.getElementById('departureDateRow');
    const departureChips = document.getElementById('departureDateChips');
    const returnRow = document.getElementById('returnDateRow');
    const returnChips = document.getElementById('returnDateChips');

    if (!form || !from || !to || !date || !panel) {
        return;
    }

    const endpoint = form.dataset.availabilityUrl;
    const MAX_CHIPS = 10;
    const collator = new Intl.Collator('vi', { sensitivity: 'base' });
    const locale = (window.RAILJET_LANG || document.documentElement.lang || 'vi') === 'en' ? 'en-GB' : 'vi-VN';
    const t = function (key, fallback) {
        const args = Array.prototype.slice.call(arguments, 2);
        if (typeof window.railT === 'function') {
            return window.railT.apply(null, [key, fallback].concat(args));
        }
        return String(fallback).replace(/\{(\d+)\}/g, function (m, i) { return args[i] !== undefined ? args[i] : m; });
    };

    let controller = null;
    let requestNo = 0;

    // Không cho chọn ngày đã qua; chưa chọn ngày thì mặc định hôm nay.
    const today = toIsoDate(new Date());
    date.min = today;
    if (!date.value) {
        date.value = today;
    }
    if (returnDate && !returnDate.min) {
        returnDate.min = today;
    }

    // Lưu tên gốc của ga để thêm / bỏ hậu tố "không có chuyến".
    [from, to].forEach(function (select) {
        Array.prototype.forEach.call(select.options, function (option) {
            option.dataset.label = option.textContent.trim();
        });
        orderOptions(select, null);
    });

    function toIsoDate(d) {
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return d.getFullYear() + '-' + month + '-' + day;
    }

    function formatDate(iso) {
        const parts = iso.split('-');
        return parts[2] + '/' + parts[1] + '/' + parts[0];
    }

    function stationName(select) {
        const option = select.options[select.selectedIndex];
        return option ? (option.dataset.label || option.textContent.trim()) : '';
    }

    function fetchAvailability(departureId, arrivalId, day, signal) {
        const params = new URLSearchParams();
        if (departureId) params.set('departureStationId', departureId);
        if (arrivalId) params.set('arrivalStationId', arrivalId);
        if (day) params.set('date', day);

        return fetch(endpoint + '?' + params.toString(), {
            headers: { 'Accept': 'application/json' },
            signal: signal
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        });
    }

    /**
     * Sắp xếp ga A → Z; ga có chuyến lên trước, ga không có chuyến gom vào nhóm cuối (bị khóa).
     * availableIds = null nghĩa là chưa có dữ liệu -> chỉ sắp xếp.
     * Ga đang chọn luôn giữ nguyên để người dùng không mất lựa chọn.
     */
    function orderOptions(select, availableIds) {
        const available = availableIds ? new Set(availableIds.map(String)) : null;
        const selectedValue = select.value;
        const options = Array.prototype.filter.call(select.querySelectorAll('option'), function (o) { return o.value; });
        const withTrips = [];
        const withoutTrips = [];

        options.forEach(function (option) {
            const hasTrips = !available || available.has(option.value);
            const isSelected = option.value === selectedValue;
            option.disabled = !hasTrips && !isSelected;
            option.textContent = hasTrips || !isSelected
                ? option.dataset.label
                : option.dataset.label + ' · ' + t('js.noTripsSuffix', 'no trains');
            (hasTrips || isSelected ? withTrips : withoutTrips).push(option);
        });

        const byName = function (a, b) { return collator.compare(a.dataset.label, b.dataset.label); };
        const regionOf = function (option) { return Number(option.dataset.region || 9); };
        // Ga có chuyến: gom theo vùng (Miền Bắc -> Trung -> Nam), trong vùng sắp theo tên.
        withTrips.sort(function (a, b) { return (regionOf(a) - regionOf(b)) || byName(a, b); });
        withoutTrips.sort(byName);

        select.querySelectorAll('optgroup').forEach(function (group) { group.remove(); });

        let regionGroup = null;
        let currentRegion = null;
        withTrips.forEach(function (option) {
            const region = regionOf(option);
            if (region !== currentRegion) {
                regionGroup = document.createElement('optgroup');
                regionGroup.label = option.dataset.regionLabel || '';
                select.appendChild(regionGroup);
                currentRegion = region;
            }
            regionGroup.appendChild(option);
        });

        if (withoutTrips.length) {
            const group = document.createElement('optgroup');
            group.label = t('js.stationsWithoutTrips', 'No matching trains');
            withoutTrips.forEach(function (option) { group.appendChild(option); });
            select.appendChild(group);
        }

        select.value = selectedValue;
    }

    function renderChips(container, row, dates, selected, onPick) {
        container.innerHTML = '';
        const list = (dates || []).slice(0, MAX_CHIPS);
        row.hidden = list.length === 0;

        list.forEach(function (iso) {
            const button = document.createElement('button');
            const d = new Date(iso + 'T00:00:00');
            const weekday = d.toLocaleDateString(locale, { weekday: 'short' });

            button.type = 'button';
            button.className = 'rj-date-chip' + (iso === selected ? ' is-active' : '');
            button.setAttribute('aria-pressed', iso === selected ? 'true' : 'false');
            button.innerHTML = '<span>' + formatDate(iso).slice(0, 5) + '</span><small>' + weekday + '</small>';
            button.title = formatDate(iso);
            button.addEventListener('click', function () { onPick(iso); });
            container.appendChild(button);
        });
    }

    function setHint(message) {
        hint.textContent = message || '';
    }

    function refresh() {
        const departureId = from.value;
        const arrivalId = to.value;
        const day = date.value;
        const wantReturn = roundTrip && roundTrip.checked && returnDate && departureId && arrivalId;
        const current = ++requestNo;

        if (controller) {
            controller.abort();
        }
        controller = new AbortController();

        const requests = [fetchAvailability(departureId, arrivalId, day, controller.signal)];
        if (wantReturn) {
            requests.push(fetchAvailability(arrivalId, departureId, returnDate.value, controller.signal));
        }

        Promise.all(requests).then(function (results) {
            if (current !== requestNo) {
                return; // đã có yêu cầu mới hơn
            }

            const outbound = results[0];
            const inbound = results[1];
            const outboundDates = outbound.availableDates || [];

            orderOptions(from, outbound.departureStationIds);
            orderOptions(to, outbound.arrivalStationIds);

            renderChips(departureChips, departureRow, outboundDates, day, function (iso) {
                date.value = iso;
                date.dispatchEvent(new Event('change', { bubbles: true }));
            });

            let message = '';
            if (departureId && arrivalId) {
                if (!outboundDates.length) {
                    message = t('js.noTripsSoon', 'No trains from {0} to {1} in the next 60 days.', stationName(from), stationName(to));
                } else if (day && outboundDates.indexOf(day) === -1) {
                    message = t('js.noTripsOnDate', 'No trains from {0} to {1} on {2}. Pick a date with trains below.',
                        stationName(from), stationName(to), formatDate(day));
                }
            } else if (day && !(outbound.departureStationIds || []).length) {
                message = t('js.noDeparturesOnDate', 'No trains depart on {0}.', formatDate(day));
            }

            if (inbound) {
                const returnDates = (inbound.availableDates || []).filter(function (iso) {
                    return !day || iso >= day;
                });
                renderChips(returnChips, returnRow, returnDates, returnDate.value, function (iso) {
                    returnDate.value = iso;
                    returnDate.dispatchEvent(new Event('change', { bubbles: true }));
                });
                if (!message && returnDate.value && returnDates.indexOf(returnDate.value) === -1) {
                    message = t('js.noReturnOnDate', 'No return trains from {0} to {1} on {2}.',
                        stationName(to), stationName(from), formatDate(returnDate.value));
                }
            } else if (returnRow) {
                returnRow.hidden = true;
            }

            setHint(message);
            panel.hidden = !message && departureRow.hidden && (!returnRow || returnRow.hidden);
        }).catch(function (error) {
            if (error.name === 'AbortError' || current !== requestNo) {
                return;
            }
            // Không chặn người dùng: vẫn tìm chuyến bình thường được.
            orderOptions(from, null);
            orderOptions(to, null);
            departureRow.hidden = true;
            if (returnRow) returnRow.hidden = true;
            setHint(t('js.availabilityError', 'Could not load train availability. You can still search.'));
            panel.hidden = false;
        });
    }

    [from, to, date, returnDate, roundTrip, oneWay].forEach(function (el) {
        if (el) {
            el.addEventListener('change', refresh);
        }
    });

    if (swap) {
        // chạy sau handler đổi chiều ở trên
        swap.addEventListener('click', function () { setTimeout(refresh, 0); });
    }

    refresh();
});
