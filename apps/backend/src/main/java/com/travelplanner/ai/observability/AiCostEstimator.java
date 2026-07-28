package com.travelplanner.ai.observability;

import com.travelplanner.domain.ai.LlmEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Turns token counts into an estimated cost for {@code ai_call_log} (PLAN §5.3 "cost per call").
 *
 * <p><strong>{@link BigDecimal}, not {@code double}</strong> — this is money (PLAN §6, §13.1). Per-call
 * amounts are fractions of a cent, and a {@code double} accumulating a million of them drifts by an
 * amount somebody eventually has to explain.
 *
 * <p>An <em>estimate</em>, and named as one. Provider list prices change without notice and are not
 * fetched at runtime, so this is for spotting a feature that suddenly costs ten times more — not for
 * reconciling an invoice. An unknown model yields zero rather than a guess: a wrong number in a cost
 * dashboard is worse than a visibly missing one.
 *
 * <p>Cached input tokens are billed at roughly a tenth of the input rate by both providers, which is
 * the entire reason {@code LlmEvent.Usage} carries them separately. Ignoring the discount would
 * overstate the cost of a long chat several-fold, and long chats are the product.
 */
public final class AiCostEstimator {

    /** Per million tokens, in USD: model to {@code [input, output]}. */
    private static final Map<String, BigDecimal[]> RATES = Map.of(
            "claude-sonnet-4-5", new BigDecimal[] {new BigDecimal("3.00"), new BigDecimal("15.00")},
            "claude-haiku-4-5", new BigDecimal[] {new BigDecimal("1.00"), new BigDecimal("5.00")},
            "gpt-4.1-mini", new BigDecimal[] {new BigDecimal("0.40"), new BigDecimal("1.60")},
            "gpt-4.1", new BigDecimal[] {new BigDecimal("2.00"), new BigDecimal("8.00")},
            "text-embedding-3-small", new BigDecimal[] {new BigDecimal("0.02"), BigDecimal.ZERO});

    private static final BigDecimal PER_MILLION = new BigDecimal("1000000");
    private static final BigDecimal CACHE_READ_DISCOUNT = new BigDecimal("0.10");
    private static final int SCALE = 6;

    private AiCostEstimator() {
    }

    /** @return USD, scaled to 6 decimal places; {@link BigDecimal#ZERO} for an unpriced model */
    public static BigDecimal estimate(String model, LlmEvent.Usage usage) {
        BigDecimal[] rates = RATES.get(model);
        if (rates == null || usage == null) {
            return BigDecimal.ZERO;
        }
        int fullPriceInput = Math.max(0, usage.inputTokens() - usage.cachedTokens());
        BigDecimal input = rates[0].multiply(BigDecimal.valueOf(fullPriceInput));
        BigDecimal cached = rates[0].multiply(BigDecimal.valueOf(usage.cachedTokens()))
                .multiply(CACHE_READ_DISCOUNT);
        BigDecimal output = rates[1].multiply(BigDecimal.valueOf(usage.outputTokens()));
        return input.add(cached).add(output).divide(PER_MILLION, SCALE, RoundingMode.HALF_UP);
    }
}
