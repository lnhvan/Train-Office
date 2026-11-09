document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.password-toggle').forEach(function (button) {
        button.addEventListener('click', function () {
            const input = button.closest('.auth-input-wrap').querySelector('.password-input');
            const icon = button.querySelector('i');

            if (!input || !icon) {
                return;
            }

            if (input.type === 'password') {
                input.type = 'text';
                icon.className = 'bi bi-eye';
            } else {
                input.type = 'password';
                icon.className = 'bi bi-eye-slash';
            }
        });
    });

    document.querySelectorAll('form[data-disable-on-submit="true"]').forEach(function (form) {
        form.addEventListener('submit', function () {
            const button = form.querySelector('button[type="submit"]');

            if (button) {
                button.disabled = true;
            }
        });
    });

    // Countdown timer cho OTP đặt lại mật khẩu (TTL 5 phút trong Redis)
    const timerBox = document.getElementById('otpTimerBox');
    const otpClock = document.getElementById('otpClock');
    const btnResend = document.getElementById('btnResendOtp');

    if (timerBox && otpClock) {
        let remainingSeconds = parseInt(timerBox.dataset.remainingSeconds, 10) || 0;
        let cooldownSeconds = parseInt(timerBox.dataset.cooldownSeconds, 10) || 0;

        function formatTime(sec) {
            const m = Math.floor(sec / 60);
            const s = sec % 60;
            return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
        }

        function updateOtpClock() {
            if (remainingSeconds > 0) {
                otpClock.textContent = formatTime(remainingSeconds);
                remainingSeconds--;
            } else {
                otpClock.textContent = '00:00';
                timerBox.classList.add('rj-otp-expired');
                const label = timerBox.querySelector('.rj-otp-timer-text');
                if (label && timerBox.dataset.expiredText) {
                    label.textContent = timerBox.dataset.expiredText;
                }
            }
        }

        const originalResendText = btnResend ? btnResend.textContent.trim() : '';

        function updateResendCooldown() {
            if (!btnResend) return;
            if (cooldownSeconds > 0) {
                btnResend.disabled = true;
                btnResend.setAttribute('disabled', 'disabled');
                btnResend.textContent = originalResendText + ' (' + cooldownSeconds + 's)';
                cooldownSeconds--;
            } else {
                btnResend.disabled = false;
                btnResend.removeAttribute('disabled');
                btnResend.textContent = originalResendText;
            }
        }

        if (remainingSeconds > 0) {
            timerBox.style.display = 'inline-flex';
            updateOtpClock();
            setInterval(updateOtpClock, 1000);
        }

        if (cooldownSeconds > 0) {
            updateResendCooldown();
            const cdInterval = setInterval(function () {
                updateResendCooldown();
                if (cooldownSeconds < 0) {
                    clearInterval(cdInterval);
                }
            }, 1000);
        }
    }
});