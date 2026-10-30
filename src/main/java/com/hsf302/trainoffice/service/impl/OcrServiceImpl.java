package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.CccdOcrResult;
import com.hsf302.trainoffice.service.OcrService;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrServiceImpl implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrServiceImpl.class);

    // Date like dd/MM/yyyy, tolerant of spaces and of . or - separators.
    private static final Pattern DATE = Pattern.compile(
            "(\\d{1,2})\\s*[/.\\-]\\s*(\\d{1,2})\\s*[/.\\-]\\s*(\\d{4})");
    // 12 consecutive digits (CCCD) or 9 (old CMND) once spaces are removed.
    private static final Pattern ID_12 = Pattern.compile("\\d{12}");
    private static final Pattern ID_9 = Pattern.compile("\\d{9}");

    // Accent-stripped, lower-cased wording printed on a Vietnamese CCCD, used to
    // tell a real card apart from an unrelated photo.
    private static final String[] CCCD_KEYWORDS = {
            "can cuoc", "cong dan", "citizen identity", "identity card",
            "cong hoa xa hoi", "socialist republic", "chu nghia viet nam"
    };

    @Value("${ocr.tessdata-path:tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:vie+eng}")
    private String language;

    // Crop region (as fractions of the framed card) that holds Số/No + Ngày sinh
    // + Giới tính, i.e. the text column to the right of the portrait. Tunable in
    // application.properties without recompiling.
    @Value("${ocr.dob-crop.x:0.30}")
    private double cropX;
    @Value("${ocr.dob-crop.y:0.28}")
    private double cropY;
    @Value("${ocr.dob-crop.w:0.70}")
    private double cropW;
    @Value("${ocr.dob-crop.h:0.45}")
    private double cropH;

    @Override
    public CccdOcrResult recognizeCccd(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return CccdOcrResult.failure("Vui lòng chọn ảnh CCCD.");
        }

        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(image.getBytes()));
        } catch (Exception ex) {
            return CccdOcrResult.failure("Không đọc được ảnh. Hãy thử ảnh JPG/PNG rõ nét.");
        }
        if (original == null) {
            return CccdOcrResult.failure("Định dạng ảnh không hợp lệ.");
        }

        String textPass;
        String digitsPass;
        List<String> digitsVariants = new ArrayList<>();
        try {
            // Text pass: full card in grayscale -> name, gender, labelled lines.
            BufferedImage gray = toGrayUpscaled(original, 1200);
            textPass = ocr(gray, false);

            // Digits pass: crop to the number/DOB column, upscale hard, binarize.
            // We read it several times with different preprocessing so a single
            // mis-read digit (e.g. 0 vs 1 in the day) can be out-voted when the
            // date of birth is parsed below.
            BufferedImage regionBase = cropRegion(original);
            digitsVariants.add(ocr(binarize(toGrayUpscaled(regionBase, 1600)), true));
            digitsVariants.add(ocr(binarize(toGrayUpscaled(regionBase, 2000)), true));
            digitsVariants.add(ocr(toGrayUpscaled(regionBase, 1600), true)); // grayscale, no binarize
            digitsPass = digitsVariants.get(0);
        } catch (Throwable ex) {
            // Throwable: Tess4J may throw UnsatisfiedLinkError if native libs / tessdata are missing.
            return CccdOcrResult.failure("OCR lỗi: " + ex.getMessage()
                    + ". Kiểm tra thư mục tessdata và file vie.traineddata.");
        }

        log.info("===== CCCD OCR text pass =====\n{}\n===== CCCD OCR digits pass =====\n{}\n=====",
                textPass, digitsPass);

        String id = extractId(textPass);
        for (String variant : digitsVariants) {
            String variantId = extractId(variant);
            if (variantId != null) {
                id = variantId;
                break;
            }
        }
        LocalDate dob = voteDateOfBirth(textPass, digitsVariants);
        String gender = extractGender(textPass);
        String fullName = extractFullName(textPass);

        // Reject non-CCCD photos: only trust the result when the image shows the
        // card's printed wording or a readable ID number. The frontend uses this
        // success=false to pop its "ảnh không hợp lệ" modal.
        if (!looksLikeCccd(textPass, id, dob, fullName)) {
            CccdOcrResult invalid = CccdOcrResult.failure(
                    "Ảnh không phải CCCD. Vui lòng chọn ảnh mặt trước thẻ CCCD, chụp rõ nét và cắt sát viền.");
            invalid.setRawText("[TEXT]\n" + textPass + "\n[DIGITS]\n" + digitsPass);
            log.info("CCCD OCR rejected: not a CCCD. id='{}'", id);
            return invalid;
        }

        CccdOcrResult result = new CccdOcrResult();
        result.setRawText("[TEXT]\n" + textPass + "\n[DIGITS]\n" + digitsPass);
        result.setIdentityNumber(id);
        result.setDateOfBirth(dob);
        result.setGender(gender);
        result.setFullName(fullName);

        boolean anyFound = id != null || dob != null || fullName != null;
        result.setSuccess(anyFound);
        result.setMessage(anyFound
                ? "Đã nhận dạng. Vui lòng kiểm tra lại các trường trước khi tiếp tục."
                : "Không bóc tách được thông tin. Hãy nhập tay hoặc chụp lại rõ hơn.");

        log.info("CCCD OCR parsed -> name='{}', id='{}', dob={}, gender={}", fullName, id, dob, gender);
        return result;
    }

    // ---------------------------------------------------------------------
    // Image preprocessing (pure java.awt, runs locally, no external service)
    // ---------------------------------------------------------------------

    /** Crop to the fraction of the image that holds the number/DOB column. */
    private BufferedImage cropRegion(BufferedImage src) {
        int x = clamp((int) Math.round(src.getWidth() * cropX), 0, src.getWidth() - 1);
        int y = clamp((int) Math.round(src.getHeight() * cropY), 0, src.getHeight() - 1);
        int w = clamp((int) Math.round(src.getWidth() * cropW), 1, src.getWidth() - x);
        int h = clamp((int) Math.round(src.getHeight() * cropH), 1, src.getHeight() - y);
        return src.getSubimage(x, y, w, h);
    }

    /** Convert to grayscale and upscale so the shorter side reaches minSide px. */
    private BufferedImage toGrayUpscaled(BufferedImage src, int minSide) {
        int width = src.getWidth();
        int height = src.getHeight();
        int shortSide = Math.min(width, height);

        double scale = (shortSide > 0 && shortSide < minSide) ? (double) minSide / shortSide : 1.0;
        int targetWidth = (int) Math.round(width * scale);
        int targetHeight = (int) Math.round(height * scale);

        BufferedImage gray = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return gray;
    }

    /** Binarize a grayscale image to pure black/white using Otsu's threshold. */
    private BufferedImage binarize(BufferedImage gray) {
        int width = gray.getWidth();
        int height = gray.getHeight();
        Raster raster = gray.getRaster();

        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                histogram[raster.getSample(x, y, 0)]++;
            }
        }

        int threshold = otsuThreshold(histogram, width * height);

        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster out = binary.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = raster.getSample(x, y, 0) > threshold ? 255 : 0;
                out.setSample(x, y, 0, value);
            }
        }
        return binary;
    }

    /** Otsu: pick the gray level that best separates foreground from background. */
    private int otsuThreshold(int[] histogram, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (double) i * histogram[i];
        }

        double sumBackground = 0;
        int weightBackground = 0;
        double maxVariance = -1;
        int threshold = 127;

        for (int i = 0; i < 256; i++) {
            weightBackground += histogram[i];
            if (weightBackground == 0) {
                continue;
            }
            int weightForeground = total - weightBackground;
            if (weightForeground == 0) {
                break;
            }
            sumBackground += (double) i * histogram[i];
            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sum - sumBackground) / weightForeground;
            double between = (double) weightBackground * weightForeground
                    * (meanBackground - meanForeground) * (meanBackground - meanForeground);
            if (between > maxVariance) {
                maxVariance = between;
                threshold = i;
            }
        }
        return threshold;
    }

    // ---------------------------------------------------------------------
    // Tesseract passes
    // ---------------------------------------------------------------------

    private String ocr(BufferedImage image, boolean digitsOnly) throws Exception {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(language);
        tesseract.setOcrEngineMode(1);              // LSTM engine only
        tesseract.setTessVariable("user_defined_dpi", "300");
        if (digitsOnly) {
            // PSM 6 = a single uniform block; restrict output to digits/separators
            // so numbers can no longer be misread as letters.
            tesseract.setPageSegMode(6);
            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789/.-");
        }
        return tesseract.doOCR(image);
    }

    // ---------------------------------------------------------------------
    // Field extraction
    // ---------------------------------------------------------------------

    /**
     * Whether the scanned image is actually a Vietnamese CCCD: a readable ID
     * number, or the card's printed wording, or both a birth date and a name.
     */
    private boolean looksLikeCccd(String textPass, String id, LocalDate dob, String fullName) {
        if (id != null) {
            return true;
        }
        String normalized = stripAccents(textPass).toLowerCase();
        for (String keyword : CCCD_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return dob != null && fullName != null;
    }

    private String extractId(String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\\r?\\n")) {
            String digitsOnly = line.replaceAll("\\s", "");
            Matcher m12 = ID_12.matcher(digitsOnly);
            if (m12.find()) {
                return m12.group();
            }
        }
        for (String line : text.split("\\r?\\n")) {
            String digitsOnly = line.replaceAll("\\s", "");
            Matcher m9 = ID_9.matcher(digitsOnly);
            if (m9.find()) {
                return m9.group();
            }
        }
        return null;
    }

    /**
     * Prefer the date on a line labelled "sinh" / "birth" (from the text pass);
     * otherwise take the earliest plausible past date across both passes
     * (birth date is always earlier than the issue/expiry dates).
     */
    private LocalDate extractDateOfBirth(String textPass, String digitsPass) {
        for (String line : textPass.split("\\r?\\n")) {
            String normalized = stripAccents(line).toLowerCase();
            if (normalized.contains("sinh") || normalized.contains("birth")) {
                LocalDate labelled = firstValidDate(line);
                if (labelled != null) {
                    return labelled;
                }
            }
        }

        List<LocalDate> dates = new ArrayList<>();
        collectDates(digitsPass, dates);
        collectDates(textPass, dates);

        LocalDate today = LocalDate.now();
        return dates.stream()
                .filter(d -> !d.isAfter(today) && d.getYear() >= 1900)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Read the date of birth from several OCR variants and keep the value the
     * most passes agree on (majority vote), so a single mis-read digit such as
     * 0 vs 1 in the day gets out-voted. Ties break to the earliest date, since
     * the birth date is always earlier than the issue/expiry dates on the card.
     */
    private LocalDate voteDateOfBirth(String textPass, List<String> digitsVariants) {
        List<LocalDate> votes = new ArrayList<>();
        LocalDate fromText = extractDateOfBirth(textPass, "");
        if (fromText != null) {
            votes.add(fromText);
        }
        for (String digits : digitsVariants) {
            LocalDate fromDigits = earliestDate(digits);
            if (fromDigits != null) {
                votes.add(fromDigits);
            }
        }
        return majorityEarliest(votes);
    }

    private LocalDate earliestDate(String text) {
        List<LocalDate> dates = new ArrayList<>();
        collectDates(text, dates);
        LocalDate today = LocalDate.now();
        return dates.stream()
                .filter(d -> !d.isAfter(today) && d.getYear() >= 1900)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private LocalDate majorityEarliest(List<LocalDate> votes) {
        LocalDate best = null;
        int bestCount = -1;
        for (LocalDate candidate : votes) {
            int count = 0;
            for (LocalDate other : votes) {
                if (other.equals(candidate)) {
                    count++;
                }
            }
            if (count > bestCount || (count == bestCount && (best == null || candidate.isBefore(best)))) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    private void collectDates(String text, List<LocalDate> target) {
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            LocalDate date = buildDate(m.group(1), m.group(2), m.group(3));
            if (date != null) {
                target.add(date);
            }
        }
    }

    private LocalDate firstValidDate(String line) {
        Matcher m = DATE.matcher(line);
        while (m.find()) {
            LocalDate date = buildDate(m.group(1), m.group(2), m.group(3));
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private LocalDate buildDate(String dayStr, String monthStr, String yearStr) {
        try {
            int day = Integer.parseInt(dayStr);
            int month = Integer.parseInt(monthStr);
            int year = Integer.parseInt(yearStr);
            if (month < 1 || month > 12 || day < 1 || day > 31
                    || year < 1900 || year > LocalDate.now().getYear()) {
                return null;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractGender(String text) {
        String normalized = stripAccents(text).toLowerCase();
        if (normalized.matches("(?s).*\\bnu\\b.*")) {
            return "FEMALE";
        }
        if (normalized.matches("(?s).*\\bnam\\b.*")) {
            return "MALE";
        }
        return null;
    }

    private String extractFullName(String text) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String normalized = stripAccents(lines[i]).toLowerCase();
            if (normalized.contains("ho va ten") || normalized.contains("full name")) {
                String sameLine = cleanName(lines[i].replaceAll("(?i).*name", "")
                        .replaceAll("(?i).*t[eê]n", ""));
                if (sameLine != null) {
                    return sameLine;
                }
                if (i + 1 < lines.length) {
                    String next = cleanName(lines[i + 1]);
                    if (next != null) {
                        return next;
                    }
                }
            }
        }
        for (String line : lines) {
            String candidate = cleanName(line);
            if (candidate != null && candidate.trim().split("\\s+").length >= 2
                    && candidate.equals(candidate.toUpperCase())) {
                return candidate;
            }
        }
        return null;
    }

    private String cleanName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^\\p{L} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() >= 3 ? cleaned : null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private String stripAccents(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
    }
}
