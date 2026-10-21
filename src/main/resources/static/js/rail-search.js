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