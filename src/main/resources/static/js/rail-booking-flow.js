/* i18n: chuỗi lấy từ window.RAILJET_I18N (fragments/i18n-js.html), có giá trị mặc định tiếng Anh */
function rjT(key, fallback) {
    const args = Array.prototype.slice.call(arguments, 2);
    if (typeof window.railT === 'function') {
        return window.railT.apply(null, [key, fallback].concat(args));
    }
    return String(fallback).replace(/\{(\d+)\}/g, function (match, index) {
        return args[index] !== undefined ? args[index] : match;
    });
}

document.addEventListener('DOMContentLoaded', function () {
    setupPassengerValidation();
    setupSeatSelection();
});

function setupPassengerValidation() {
    const passengerCards = document.querySelectorAll('.rail-passenger-card');
    const form = document.querySelector('.rail-passenger-info-form');

    if (!passengerCards.length) {
        return;
    }

    function getAge(dobString) {
        if (!dobString) {
            return -1;
        }

        const today = new Date();
        const parts = dobString.split('-').map(Number);
        const birthDate = new Date(parts[0], parts[1] - 1, parts[2]);

        let age = today.getFullYear() - birthDate.getFullYear();
        const monthDiff = today.getMonth() - birthDate.getMonth();

        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
            age--;
        }

        return age;
    }

    function validateCard(card) {
        const dobInput = card.querySelector('.dob-input');
        const typeDisplay = card.querySelector('.passenger-type-display');
        const typeHidden = card.querySelector('.passenger-type-hidden');
        const errorMsg = card.querySelector('.age-validation-msg');

        const identityWrapper = card.querySelector('.identity-wrapper');
        const identityInput = card.querySelector('.identity-input');

        const relationshipWrapper = card.querySelector('.relationship-wrapper');
        const relationshipInput = card.querySelector('.relationship-input');

        if (!dobInput || !typeDisplay || !typeHidden) {
            return true;
        }

        const age = getAge(dobInput.value);
        const valid = !dobInput.value || age >= 0;

        let matchingOption = null;
        if (valid && dobInput.value) {
            matchingOption = Array.from(typeDisplay.options).find(function (option) {
                if (!option.dataset.minAge && !option.dataset.maxAge) {
                    return false;
                }
                const minAge = option.dataset.minAge === '' ? null : Number(option.dataset.minAge);
                const maxAge = option.dataset.maxAge === '' ? null : Number(option.dataset.maxAge);
                return (minAge === null || age >= minAge) && (maxAge === null || age <= maxAge);
            }) || null;
        }

        if (!dobInput.value) {
            typeDisplay.options[0].textContent = rjT('js.enterDob', 'Enter date of birth');
            typeDisplay.value = 'DEFAULT';
            typeHidden.value = 'DEFAULT';
        } else if (matchingOption) {
            typeDisplay.value = matchingOption.value;
            typeHidden.value = matchingOption.value;
        } else {
            typeDisplay.options[0].textContent = rjT('js.defaultZero', 'Default - 0%');
            typeDisplay.value = 'DEFAULT';
            typeHidden.value = 'DEFAULT';
        }

        if (errorMsg) {
            errorMsg.classList.toggle('show', !valid);
        }

        dobInput.classList.toggle('is-invalid', !valid);

        if (identityWrapper && identityInput) {
            if (age >= 16) {
                identityWrapper.style.display = 'block';
                identityInput.required = true;
            } else {
                identityWrapper.style.display = 'none';
                identityInput.required = false;
            }
        }

        if (relationshipWrapper && relationshipInput) {
            if (age >= 0 && age < 16) {
                relationshipWrapper.style.display = 'block';
                relationshipInput.required = true;
            } else {
                relationshipWrapper.style.display = 'none';
                relationshipInput.required = false;
            }
        }

        return valid;
    }

    passengerCards.forEach(function (card) {
        const dobInput = card.querySelector('.dob-input');

        if (dobInput) {
            dobInput.addEventListener('change', function () {
                validateCard(card);
            });
        }

        validateCard(card);
    });

    if (form) {
        form.addEventListener('submit', function (event) {
            let valid = true;
            passengerCards.forEach(function (card) {
                if (!validateCard(card)) {
                    valid = false;
                }
            });
            if (!valid) {
                event.preventDefault();
            }
        });
    }
}

function setupSeatSelection() {
    const form = document.getElementById('railSeatSelectionForm');

    if (!form) {
        return;
    }

    const seatOptions = Array.from(document.querySelectorAll('.rj-seat-option'));
    const checkboxes = Array.from(document.querySelectorAll('.rail-seat-checkbox'));

    const passengerCountInput = document.getElementById('passengerCountInput');
    const seatLimitHint = document.getElementById('seatLimitHint');
    const maxPassengers = Number(form.dataset.maxPassengers || 10);
    const selectedList = document.getElementById('selectedSeatList');
    const selectedCountText = document.getElementById('selectedCountText');
    const selectedTotalText = document.getElementById('selectedTotalText');

    const coachTabs = document.getElementById('railCoachTabs');
    const compartmentTabs = document.getElementById('railCompartmentTabs');
    const previousCoachBtn = document.getElementById('previousCoachBtn');
    const nextCoachBtn = document.getElementById('nextCoachBtn');

    const currentCoachName = document.getElementById('currentCoachName');
    const currentCoachMeta = document.getElementById('currentCoachMeta');
    const currentCoachSeatCount = document.getElementById('currentCoachSeatCount');

    const seatMap = document.querySelector('.rj-seat-map');

    const basePrice = Number(form.dataset.basePrice || 0);

    let coaches = [];
    let activeCoachIndex = 0;
    let activeCompartmentIndex = 0;
    function formatVnd(value) {
        return new Intl.NumberFormat('en-US').format(value) + ' VND';
    }

    function formatVndShort(value) {
        const amount = numberValue(value);

        if (amount <= 0) {
            return '0 VND';
        }

        if (amount >= 1000000) {
            const million = amount / 1000000;
            return '+' + (Number.isInteger(million) ? million : million.toFixed(1)) + 'M';
        }

        if (amount >= 1000) {
            return '+' + Math.round(amount / 1000) + 'K';
        }

        return '+' + amount;
    }

    function numberValue(value) {
        const parsed = Number(value);
        return Number.isNaN(parsed) ? 0 : parsed;
    }

    function formatBerthLevel(value) {
        const normalized = String(value || '').trim().toUpperCase();

        if (normalized === 'LOWER') {
            return rjT('js.berthLower', 'Level 1 / Lower berth');
        }

        if (normalized === 'MIDDLE') {
            return rjT('js.berthMiddle', 'Level 2 / Middle berth');
        }

        if (normalized === 'UPPER') {
            return rjT('js.berthUpper', 'Level 3 / Upper berth');
        }

        return normalized.replaceAll('_', ' ');
    }

    function isTrue(value) {
        return String(value || '').toLowerCase() === 'true';
    }

    function hasRealCompartment(option, checkbox) {
        const hasCompartment = isTrue(option.dataset.hasCompartment)
            || isTrue(checkbox.dataset.hasCompartment);

        const compartmentId = option.dataset.compartmentId || checkbox.dataset.compartmentId;

        return hasCompartment
            && compartmentId !== null
            && compartmentId !== undefined
            && String(compartmentId).trim() !== '';
    }

    function showSeatsByCoachOnly(activeCoach) {
        seatOptions.forEach(function (option) {
            const sameCoach = String(option.dataset.coachId) === String(activeCoach.id);

            option.classList.toggle('hidden-by-coach', !sameCoach);
        });
    }

    function hideAllSeats() {
        seatOptions.forEach(function (option) {
            option.classList.add('hidden-by-coach');
        });
    }

    function getSelectedCheckboxes() {
        return checkboxes.filter(function (checkbox) {
            return checkbox.checked;
        });
    }

    function getStorageKey() {
        const tripId = form.querySelector('input[name="trainTripId"]')?.value || 'trip';
        const departureId = form.querySelector('input[name="departureStationId"]')?.value || 'dep';
        const arrivalId = form.querySelector('input[name="arrivalStationId"]')?.value || 'arr';

        return 'railjet:selectedSeats:' + tripId + ':' + departureId + ':' + arrivalId;
    }

    function saveSelectedSeats() {
        const selectedSeatIds = getSelectedCheckboxes().map(function (checkbox) {
            return checkbox.value;
        });

        localStorage.setItem(getStorageKey(), JSON.stringify(selectedSeatIds));
    }

    function clearSavedSeats() {
        localStorage.removeItem(getStorageKey());
    }

    function restoreSelectedSeats() {
        const raw = localStorage.getItem(getStorageKey());

        if (!raw) {
            return null;
        }

        let savedSeatIds = [];

        try {
            savedSeatIds = JSON.parse(raw);
        } catch (error) {
            clearSavedSeats();
            return null;
        }

        if (!Array.isArray(savedSeatIds) || !savedSeatIds.length) {
            return null;
        }

        let firstRestoredCoachId = null;

        checkboxes.forEach(function (checkbox) {
            if (checkbox.disabled) {
                checkbox.checked = false;
                return;
            }

            const shouldRestore = savedSeatIds.includes(String(checkbox.value));
            checkbox.checked = shouldRestore;

            if (shouldRestore && firstRestoredCoachId === null) {
                firstRestoredCoachId = checkbox.dataset.coachId || null;
            }
        });

        saveSelectedSeats();

        return firstRestoredCoachId;
    }

    function formatCoachPrice(value) {
        const amount = numberValue(value);

        if (amount <= 0) {
            return '0 VND';
        }

        return '+' + formatVnd(amount);
    }

    function buildCoaches() {
        const coachMap = new Map();

        seatOptions.forEach(function (option) {
            const checkbox = option.querySelector('.rail-seat-checkbox');

            if (!checkbox) {
                return;
            }

            const coachId = option.dataset.coachId || checkbox.dataset.coachId || '0';
            const coachNumber = option.dataset.coachNumber || checkbox.dataset.coachNumber || rjT('js.unknown', 'Unknown');
            const coachType = option.dataset.coachType || checkbox.dataset.coachType || rjT('js.standard', 'Standard');
            const coachCapacity = option.dataset.coachCapacity || '0';
            const extraPrice = numberValue(checkbox.dataset.seatExtraPrice);

            const sleeperCoach = isTrue(option.dataset.isSleeper) || isTrue(checkbox.dataset.isSleeper);

            if (!coachMap.has(coachId)) {
                coachMap.set(coachId, {
                    id: String(coachId),
                    number: coachNumber,
                    type: coachType,
                    capacity: coachCapacity,
                    isSleeper: sleeperCoach,
                    totalSeats: 0,
                    availableSeats: 0,
                    minExtraPrice: extraPrice,
                    maxExtraPrice: extraPrice,
                    compartments: new Map()
                });
            }

            const coach = coachMap.get(coachId);

            coach.totalSeats += 1;

            if (!option.classList.contains('booked')) {
                coach.availableSeats += 1;
            }

            coach.minExtraPrice = Math.min(coach.minExtraPrice, extraPrice);
            coach.maxExtraPrice = Math.max(coach.maxExtraPrice, extraPrice);

            if (!coach.isSleeper || !hasRealCompartment(option, checkbox)) {
                return;
            }

            const compartmentId = String(option.dataset.compartmentId || checkbox.dataset.compartmentId);
            const compartmentNumber = option.dataset.compartmentNumber || checkbox.dataset.compartmentNumber;

            if (!coach.compartments.has(compartmentId)) {
                coach.compartments.set(compartmentId, {
                    id: compartmentId,
                    number: compartmentNumber,
                    totalSeats: 0,
                    availableSeats: 0
                });
            }

            const compartment = coach.compartments.get(compartmentId);

            compartment.totalSeats += 1;

            if (!option.classList.contains('booked')) {
                compartment.availableSeats += 1;
            }
        });

        coaches = Array.from(coachMap.values()).sort(function (a, b) {
            return String(a.number).localeCompare(String(b.number), undefined, {
                numeric: true,
                sensitivity: 'base'
            });
        });

        coaches.forEach(function (coach) {
            coach.compartments = Array.from(coach.compartments.values()).sort(function (a, b) {
                return String(a.number).localeCompare(String(b.number), undefined, {
                    numeric: true,
                    sensitivity: 'base'
                });
            });
        });
    }

    function getCoachPriceText(coach) {
        if (!coach) {
            return rjT('js.included', 'Included');
        }

        if (coach.minExtraPrice === coach.maxExtraPrice) {
            return formatCoachPrice(coach.minExtraPrice);
        }

        return formatCoachPrice(coach.minExtraPrice) + ' - ' + formatCoachPrice(coach.maxExtraPrice);
    }

    function getCoachPriceShortText(coach) {
        if (!coach) {
            return rjT('js.included', 'Included');
        }

        if (coach.minExtraPrice === coach.maxExtraPrice) {
            return formatVndShort(coach.minExtraPrice);
        }

        return formatVndShort(coach.minExtraPrice) + ' - ' + formatVndShort(coach.maxExtraPrice);
    }

    function renderCoachTabs() {
        if (!coachTabs) {
            return;
        }

        coachTabs.innerHTML = '';

        coaches.forEach(function (coach, index) {
            const button = document.createElement('button');

            button.type = 'button';
            button.className = 'rj-coach-tab';
            button.dataset.coachId = coach.id;
            button.title = rjT('js.coachPriceTitle', 'Coach price: {0}', getCoachPriceText(coach));

            button.innerHTML =
                '<i class="bi bi-train-front"></i>' +
                '<div class="rj-coach-tab-content">' +
                '<strong>' + rjT('js.coachN', 'Coach {0}', coach.number) + '</strong>' +
                '<small>' + coach.type + ' • ' + rjT('js.availableOf', '{0}/{1} available', coach.availableSeats, coach.totalSeats) + '</small>' +
                '<em class="rj-coach-price">' + getCoachPriceShortText(coach) + '</em>' +
                '</div>';

            button.addEventListener('click', function () {
                showCoach(index);
            });

            coachTabs.appendChild(button);
        });
    }

    function getCoachIndexById(coachId) {
        if (!coachId) {
            return 0;
        }

        const index = coaches.findIndex(function (coach) {
            return String(coach.id) === String(coachId);
        });

        return index >= 0 ? index : 0;
    }

    function renderCompartmentTabs(coach) {
        if (!compartmentTabs) {
            return;
        }

        compartmentTabs.innerHTML = '';

        if (!coach || !coach.isSleeper || !coach.compartments || !coach.compartments.length) {
            compartmentTabs.classList.add('is-hidden');
            return;
        }

        compartmentTabs.classList.remove('is-hidden');

        coach.compartments.forEach(function (compartment, index) {
            const button = document.createElement('button');

            button.type = 'button';
            button.className = 'rj-compartment-tab';
            button.dataset.compartmentId = compartment.id;

            button.innerHTML =
                '<strong>' + compartment.number + '</strong>' +
                '<small>' + rjT('js.berthsAvailableOf', '{0}/{1} berths available', compartment.availableSeats, compartment.totalSeats) + '</small>';

            button.addEventListener('click', function () {
                showCompartment(index);
            });

            compartmentTabs.appendChild(button);
        });
    }

    function showCompartment(index) {
        const activeCoach = coaches[activeCoachIndex];

        if (!activeCoach || !activeCoach.isSleeper || !activeCoach.compartments || !activeCoach.compartments.length) {
            showSeatsByCoachOnly(activeCoach);
            return;
        }

        if (index < 0) {
            index = activeCoach.compartments.length - 1;
        }

        if (index >= activeCoach.compartments.length) {
            index = 0;
        }

        activeCompartmentIndex = index;

        const activeCompartment = activeCoach.compartments[activeCompartmentIndex];

        hideAllSeats();

        seatOptions.forEach(function (option) {
            const sameCoach = String(option.dataset.coachId) === String(activeCoach.id);
            const sameCompartment = String(option.dataset.compartmentId || '') === String(activeCompartment.id);

            if (sameCoach && sameCompartment) {
                option.classList.remove('hidden-by-coach');
            }
        });

        document.querySelectorAll('.rj-compartment-tab').forEach(function (button) {
            button.classList.toggle('active', String(button.dataset.compartmentId) === String(activeCompartment.id));
        });

        if (currentCoachMeta) {
            currentCoachMeta.textContent = rjT(
                'js.compartmentMeta',
                '{0} • Compartment {1} • {2} available of {3} berths',
                activeCoach.type,
                activeCompartment.number,
                activeCompartment.availableSeats,
                activeCompartment.totalSeats
            );
        }

        if (currentCoachSeatCount) {
            currentCoachSeatCount.textContent =
                rjT('js.compartmentSeatCount', '{0} • {1} berths available', activeCompartment.number, activeCompartment.availableSeats);
        }

        if (seatMap) {
            seatMap.scrollTop = 0;
        }
    }

    function showCoach(index) {
        if (!coaches.length) {
            return;
        }

        if (index < 0) {
            index = coaches.length - 1;
        }

        if (index >= coaches.length) {
            index = 0;
        }

        activeCoachIndex = index;
        activeCompartmentIndex = 0;

        const activeCoach = coaches[activeCoachIndex];

        // Toa giường nằm: vẽ khoang như giường tầng (2 cột, tầng trên ở trên), không có lối đi giữa.
        if (seatMap) {
            seatMap.classList.toggle('is-sleeper', Boolean(activeCoach.isSleeper));
        }

        renderCompartmentTabs(activeCoach);

        if (activeCoach.isSleeper && activeCoach.compartments && activeCoach.compartments.length) {
            showCompartment(0);
        } else {
            if (compartmentTabs) {
                compartmentTabs.classList.add('is-hidden');
                compartmentTabs.innerHTML = '';
            }

            showSeatsByCoachOnly(activeCoach);

            if (currentCoachMeta) {
                currentCoachMeta.textContent = rjT(
                    'js.coachMeta',
                    '{0} • Price: {1} • {2} available of {3} seats',
                    activeCoach.type,
                    getCoachPriceShortText(activeCoach),
                    activeCoach.availableSeats,
                    activeCoach.totalSeats
                );
            }

            if (currentCoachSeatCount) {
                currentCoachSeatCount.textContent = rjT('js.seatsAvailable', '{0} seats available', activeCoach.availableSeats);
            }
        }

        document.querySelectorAll('.rj-coach-tab').forEach(function (button) {
            button.classList.toggle('active', String(button.dataset.coachId) === String(activeCoach.id));
        });

        if (currentCoachName) {
            currentCoachName.textContent = rjT('js.coachN', 'Coach {0}', activeCoach.number);
        }

        if (previousCoachBtn) {
            previousCoachBtn.disabled = coaches.length <= 1;
        }

        if (nextCoachBtn) {
            nextCoachBtn.disabled = coaches.length <= 1;
        }

        if (seatMap) {
            seatMap.scrollTop = 0;
        }
    }

    /** Số ghế cần chọn = số hành khách (1..maxPassengers). */
    function getRequiredSeats() {
        if (!passengerCountInput) {
            return Math.max(1, getSelectedCheckboxes().length);
        }
        const value = parseInt(passengerCountInput.value, 10);
        return Math.min(maxPassengers, Math.max(1, isNaN(value) ? 1 : value));
    }

    function showSeatHint(message) {
        if (seatLimitHint) {
            seatLimitHint.textContent = message || '';
        }
    }

    /** Ghế khôi phục từ lần trước có thể nhiều hơn số hành khách đã chọn khi tìm chuyến -> bỏ bớt. */
    function trimSelectionToPassengerCount() {
        const required = getRequiredSeats();
        const selected = getSelectedCheckboxes();

        if (selected.length <= required) {
            return;
        }

        selected.slice(required).forEach(function (checkbox) {
            checkbox.checked = false;
        });
        saveSelectedSeats();
    }

    function updateSeatClasses() {
        checkboxes.forEach(function (checkbox) {
            const option = checkbox.closest('.rj-seat-option');

            if (!option) {
                return;
            }

            option.classList.toggle('selected', checkbox.checked);
        });
    }

    function getSeatExtraPrice(checkbox) {
        return numberValue(checkbox.dataset.seatExtraPrice);
    }

    function getSeatTotalPrice(checkbox) {
        return basePrice + getSeatExtraPrice(checkbox);
    }

    function updateSummary() {
        const selected = getSelectedCheckboxes();

        updateSeatClasses();

        if (selectedCountText) {
            selectedCountText.textContent = rjT('js.seatsOfPassengers', '{0} / {1}', selected.length, getRequiredSeats());
        }

        const total = selected.reduce(function (sum, checkbox) {
            return sum + getSeatTotalPrice(checkbox);
        }, 0);

        if (selectedTotalText) {
            selectedTotalText.textContent = formatVnd(total);
        }

        if (!selectedList) {
            return;
        }

        selectedList.innerHTML = '';

        if (!selected.length) {
            const empty = document.createElement('div');
            empty.className = 'rail-selected-empty';
            empty.textContent = rjT('js.noSeatsSelected', 'No seats selected yet.');
            selectedList.appendChild(empty);
            return;
        }

        selected.forEach(function (checkbox) {
            const seatLabel = checkbox.dataset.seatLabel || rjT('js.seat', 'Seat');
            const seatType = checkbox.dataset.seatType || rjT('js.standard', 'Standard');
            const coachNumber = checkbox.dataset.coachNumber || rjT('js.unknown', 'Unknown');
            const coachType = checkbox.dataset.coachType || seatType;
            const extraPrice = getSeatExtraPrice(checkbox);
            const hasCompartment = isTrue(checkbox.dataset.hasCompartment)
                && checkbox.dataset.compartmentNumber
                && checkbox.dataset.compartmentNumber.trim() !== '';

            const compartmentNumber = hasCompartment ? checkbox.dataset.compartmentNumber : '';
            const berthLevel = checkbox.dataset.berthLevel || '';
            const berthLabel = checkbox.dataset.berthLabel || formatBerthLevel(berthLevel);
            const item = document.createElement('div');
            item.className = 'rail-selected-item';

            item.innerHTML =
                '<div class="rail-selected-seat-info">' +
                '<div class="rail-selected-seat-code">' + seatLabel + '</div>' +
                '<div class="rail-selected-seat-type">' +
                '<strong>' + rjT('js.coachN', 'Coach {0}', coachNumber) + '</strong>' +
                '<small>' +
                coachType +
                (hasCompartment ? ' • ' + compartmentNumber : '') +
                (berthLevel ? ' • ' + berthLabel : '') +
                '</small>' +
                '</div>' +
                '</div>' +
                '<div class="rail-selected-price">' +
                '<span class="rail-selected-extra-label">' + rjT('js.coachPrice', 'Coach price') + '</span>' +
                '<strong>' + formatCoachPrice(extraPrice) + '</strong>' +
                '</div>' +
                '<button type="button" class="rail-remove-seat-btn" data-remove-seat-id="' + checkbox.value + '" title="' + rjT('js.removeSeat', 'Remove seat') + '">' +
                '<i class="bi bi-x-lg"></i>' +
                '</button>';

            selectedList.appendChild(item);
        });
    }

    if (previousCoachBtn) {
        previousCoachBtn.addEventListener('click', function () {
            showCoach(activeCoachIndex - 1);
        });
    }

    if (nextCoachBtn) {
        nextCoachBtn.addEventListener('click', function () {
            showCoach(activeCoachIndex + 1);
        });
    }

    checkboxes.forEach(function (checkbox) {
        checkbox.addEventListener('change', function () {
            // Mỗi hành khách một ghế: không cho chọn quá số hành khách.
            if (checkbox.checked && getSelectedCheckboxes().length > getRequiredSeats()) {
                checkbox.checked = false;
                showSeatHint(rjT(
                    'js.seatLimitReached',
                    'You have selected enough seats for {0} passengers. Increase the passenger count to add more.',
                    getRequiredSeats()
                ));
                return;
            }

            showSeatHint('');
            updateSummary();
            saveSelectedSeats();
        });
    });

    if (passengerCountInput) {
        passengerCountInput.addEventListener('change', function () {
            const selectedCount = getSelectedCheckboxes().length;

            // Không cho giảm số hành khách xuống dưới số ghế đang chọn.
            if (getRequiredSeats() < selectedCount) {
                passengerCountInput.value = selectedCount;
                passengerCountInput.dispatchEvent(new Event('rj:refresh'));
                showSeatHint(rjT('js.removeSeatsFirst', 'Remove a selected seat before lowering the passenger count.'));
            } else {
                showSeatHint('');
            }

            updateSummary();
        });
    }
    if (selectedList) {
        selectedList.addEventListener('click', function (event) {
            const removeButton = event.target.closest('.rail-remove-seat-btn');

            if (!removeButton) {
                return;
            }

            const seatId = removeButton.dataset.removeSeatId;

            const checkbox = checkboxes.find(function (item) {
                return String(item.value) === String(seatId);
            });

            if (!checkbox || checkbox.disabled) {
                return;
            }

            checkbox.checked = false;

            updateSummary();
            saveSelectedSeats();
        });
    }
    form.addEventListener('submit', function (event) {
        const selected = getSelectedCheckboxes();

        if (!selected.length || selected.length !== getRequiredSeats()) {
            event.preventDefault();
            showSeatHint(rjT('js.selectExactSeats', 'Please select {0} seats, one for each passenger.', getRequiredSeats()));
            if (seatLimitHint) {
                seatLimitHint.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            }
            return false;
        }

        if (passengerCountInput) {
            passengerCountInput.value = selected.length;
        }

        saveSelectedSeats();

        return true;
    });

    buildCoaches();
    renderCoachTabs();

    const restoredCoachId = restoreSelectedSeats();
    trimSelectionToPassengerCount();
    showCoach(getCoachIndexById(restoredCoachId));

    updateSummary();
}
