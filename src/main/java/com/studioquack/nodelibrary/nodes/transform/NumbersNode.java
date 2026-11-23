package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;

/**
 * Numbers node ({@code nodes/numbers}) — format / round / math / random_int.
 *
 * <p>{@code format} renders {@code value} with {@code precision} decimals (US locale,
 * no grouping). {@code round} rounds half-up to {@code precision} decimals.
 * {@code math} applies {@code op} (+ - * / %; INPUT wins over config) to {@code value} and {@code other}.
 * {@code random_int} draws an inclusive [{@code min}, {@code max}] integer from
 * {@link SecureRandom} (CSPRNG per the definition — never {@code java.util.Random}).
 * Integral results serialize as longs so {@code 1+2} reads as {@code 3}.
 */
public final class NumbersNode implements NodeHandler {

    public static final String DEFAULT_ID = "4e7de7da-c3af-4adc-87ab-e7ed67efa9c4";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String templateId;

    public NumbersNode() {
        this(DEFAULT_ID);
    }

    public NumbersNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.readInput(request, helper);
            String op = TransformSupport.operation(cfg, in);
            int precision = Math.max(0, Math.min(EmulatorUtil.toInt(cfg.get("precision"), 2), 20));

            return switch (op) {
                case "format" -> format(in, precision);
                case "round" -> round(in, precision);
                case "math" -> math(cfg, in);
                case "random_int" -> randomInt(in);
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("numbers failed");
        }
    }

    private Map<String, Object> format(Map<String, Object> in, int precision) {
        Double v = requiredNumber(in, "value");
        if (v == null) {
            return DataSupport.invalidInput("value is required and must be a number.");
        }
        return TransformSupport.ok(Map.of("value", String.format(Locale.US, "%." + precision + "f", v)));
    }

    private Map<String, Object> round(Map<String, Object> in, int precision) {
        Double v = requiredNumber(in, "value");
        if (v == null) {
            return DataSupport.invalidInput("value is required and must be a number.");
        }
        double rounded = BigDecimal.valueOf(v).setScale(precision, RoundingMode.HALF_UP).doubleValue();
        return TransformSupport.ok(Map.of("value", numeric(rounded)));
    }

    private Map<String, Object> math(Map<String, Object> cfg, Map<String, Object> in) {
        Double a = requiredNumber(in, "value");
        Double b = requiredNumber(in, "other");
        if (a == null || b == null) {
            return DataSupport.invalidInput("value and other are required and must be numbers.");
        }
        String operator = mathOperator(cfg, in);
        double result = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/", "%" -> {
                if (b == 0) {
                    throw new IllegalArgumentException("division by zero");
                }
                yield "/".equals(operator) ? a / b : a % b;
            }
            default -> throw new IllegalArgumentException("unknown op: " + operator);
        };
        return TransformSupport.ok(Map.of("value", numeric(result)));
    }

    /** Math operator: INPUT {@code op} wins over instance config. Default {@code +}. */
    private static String mathOperator(Map<String, Object> cfg, Map<String, Object> in) {
        String operator = DataSupport.trimToNull(EmulatorUtil.str(in == null ? null : in.get("op")));
        if (operator == null) {
            operator = DataSupport.cfgStr(cfg, "op", "+");
        }
        return operator;
    }

    private Map<String, Object> randomInt(Map<String, Object> in) {
        long min = TransformSupport.toLong(in.get("min")) != null ? TransformSupport.toLong(in.get("min")) : 0L;
        long max = TransformSupport.toLong(in.get("max")) != null ? TransformSupport.toLong(in.get("max")) : 100L;
        if (min > max) {
            return DataSupport.invalidInput("min must be <= max.");
        }
        // nextLong(origin, bound) is exclusive of bound; +1 makes max inclusive
        long value = RANDOM.nextLong(min, max + 1);
        return TransformSupport.ok(Map.of("value", value));
    }

    private static Double requiredNumber(Map<String, Object> in, String key) {
        return TransformSupport.toDouble(in.get(key));
    }

    /** Integral doubles become longs so JSON output reads naturally. */
    static Object numeric(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return (long) d;
        }
        return d;
    }
}
