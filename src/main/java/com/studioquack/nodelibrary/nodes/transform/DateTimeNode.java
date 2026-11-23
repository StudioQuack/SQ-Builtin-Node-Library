package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackHelper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Date &amp; Time node ({@code nodes/date-time}) — ISO-8601 / IANA timezone math on the
 * daemon clock. No remote time API.
 *
 * <p>Operations: {@code now}, {@code add}, {@code subtract}, {@code format},
 * {@code extract}, {@code diff}, {@code round}. Instants parse as RFC 3339 (offset or
 * {@code Z}), ISO local date-time (read in the configured zone), or ISO date (start of
 * day). {@code unit} is seconds|minutes|hours|days|weeks|months|years (default days).
 * {@code round} floors to the unit boundary (weeks start Monday, ISO).
 * {@code diff} is {@code datetime − other} in whole units (positive when datetime is
 * later). {@code extract} parts: year|month|day|hour|minute|second|dow
 * (dow is ISO: 1=Monday … 7=Sunday).
 */
public final class DateTimeNode implements NodeHandler {

    public static final String DEFAULT_ID = "3c67660e-451d-4693-8628-a6a00e8df9c2";

    private final String templateId;

    public DateTimeNode() {
        this(DEFAULT_ID);
    }

    public DateTimeNode(String templateId) {
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
            Map<String, Object> in = DataSupport.overlayConfig(
                    DataSupport.readInput(request, helper), cfg, "datetime", "amount", "other", "part");
            String op = TransformSupport.operation(cfg, in);

            ZoneId zone = zone(DataSupport.cfgStr(cfg, "timezone", "UTC"));
            if (zone == null) {
                return DataSupport.invalidInput("unknown timezone: " + DataSupport.cfgStr(cfg, "timezone", ""));
            }
            DateTimeFormatter formatter = formatter(DataSupport.cfgStr(cfg, "format", "iso"));
            if (formatter == null) {
                return DataSupport.invalidInput("invalid format pattern.");
            }
            ChronoUnit unit = unit(DataSupport.cfgStr(cfg, "unit", "days"));
            if (unit == null) {
                return DataSupport.invalidInput("unknown unit: " + DataSupport.cfgStr(cfg, "unit", ""));
            }

            return switch (op) {
                case "now" -> TransformSupport.ok(Map.of("datetime", formatter.format(ZonedDateTime.now(zone))));
                case "add", "subtract" -> addSubtract(op, cfg, in, zone, formatter, unit);
                case "format" -> {
                    ZonedDateTime zdt = requireDatetime(in, zone);
                    if (zdt == null) {
                        yield DataSupport.invalidInput("datetime is required and must be RFC 3339 / ISO-8601.");
                    }
                    yield TransformSupport.ok(Map.of("datetime", formatter.format(zdt)));
                }
                case "extract" -> extract(in, zone);
                case "diff" -> diff(in, zone, unit);
                case "round" -> round(in, zone, formatter, unit);
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("date-time failed");
        }
    }

    private Map<String, Object> addSubtract(String op, Map<String, Object> cfg, Map<String, Object> in,
                                            ZoneId zone, DateTimeFormatter formatter, ChronoUnit unit) {
        ZonedDateTime base = parse(EmulatorUtil.str(in.get("datetime")), zone);
        if (base == null) {
            // definition: datetime defaults to now for add/subtract
            base = ZonedDateTime.now(zone);
        }
        long amount = TransformSupport.toLong(in.get("amount")) != null
                ? TransformSupport.toLong(in.get("amount")) : 1L;
        ZonedDateTime result = "add".equals(op) ? base.plus(amount, unit) : base.minus(amount, unit);
        return TransformSupport.ok(Map.of("datetime", formatter.format(result)));
    }

    private Map<String, Object> extract(Map<String, Object> in, ZoneId zone) {
        ZonedDateTime zdt = requireDatetime(in, zone);
        if (zdt == null) {
            return DataSupport.invalidInput("datetime is required and must be RFC 3339 / ISO-8601.");
        }
        String part = EmulatorUtil.str(in.get("part")).trim().toLowerCase(java.util.Locale.ROOT);
        Long value = switch (part) {
            case "year" -> (long) zdt.getYear();
            case "month" -> (long) zdt.getMonthValue();
            case "day" -> (long) zdt.getDayOfMonth();
            case "hour" -> (long) zdt.getHour();
            case "minute" -> (long) zdt.getMinute();
            case "second" -> (long) zdt.getSecond();
            case "dow" -> (long) zdt.getDayOfWeek().getValue();
            default -> null;
        };
        if (value == null) {
            return DataSupport.invalidInput("unknown part: " + (part.isEmpty() ? "<missing>" : part));
        }
        return TransformSupport.ok(Map.of("part", part, "value", value));
    }

    private Map<String, Object> diff(Map<String, Object> in, ZoneId zone, ChronoUnit unit) {
        ZonedDateTime zdt = requireDatetime(in, zone);
        ZonedDateTime other = parse(EmulatorUtil.str(in.get("other")), zone);
        if (zdt == null || other == null) {
            return DataSupport.invalidInput("datetime and other are required and must be RFC 3339 / ISO-8601.");
        }
        long difference = unit.between(other, zdt);
        return TransformSupport.ok(Map.of("difference", difference, "unit", unit.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Map<String, Object> round(Map<String, Object> in, ZoneId zone,
                                      DateTimeFormatter formatter, ChronoUnit unit) {
        ZonedDateTime zdt = requireDatetime(in, zone);
        if (zdt == null) {
            return DataSupport.invalidInput("datetime is required and must be RFC 3339 / ISO-8601.");
        }
        ZonedDateTime floored = switch (unit) {
            case SECONDS, MINUTES, HOURS, DAYS -> zdt.truncatedTo(unit);
            case WEEKS -> zdt.with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
            case MONTHS -> zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case YEARS -> zdt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            default -> zdt;
        };
        return TransformSupport.ok(Map.of("datetime", formatter.format(floored)));
    }

    private static ZonedDateTime requireDatetime(Map<String, Object> in, ZoneId zone) {
        return parse(EmulatorUtil.str(in.get("datetime")), zone);
    }

    /** Tolerant ISO-8601 read: offset, zoned, instant, local date-time, or bare date. */
    static ZonedDateTime parse(String s, ZoneId zone) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return OffsetDateTime.parse(t).atZoneSameInstant(zone);
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(t);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(t).atZone(zone);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(t).atZone(zone);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(t).atStartOfDay(zone);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ZoneId zone(String name) {
        try {
            return ZoneId.of(name == null || name.isBlank() ? "UTC" : name.trim());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code iso} → RFC 3339 with fixed seconds ({@code yyyy-MM-dd'T'HH:mm:ssXXX};
     * ISO_OFFSET_DATE_TIME would drop zero seconds). Anything else is a
     * DateTimeFormatter pattern.
     */
    private static DateTimeFormatter formatter(String format) {
        try {
            if (format == null || format.isBlank() || "iso".equalsIgnoreCase(format.trim())) {
                return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            }
            return DateTimeFormatter.ofPattern(format.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ChronoUnit unit(String name) {
        if (name == null) {
            return ChronoUnit.DAYS;
        }
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "seconds" -> ChronoUnit.SECONDS;
            case "minutes" -> ChronoUnit.MINUTES;
            case "hours" -> ChronoUnit.HOURS;
            case "days" -> ChronoUnit.DAYS;
            case "weeks" -> ChronoUnit.WEEKS;
            case "months" -> ChronoUnit.MONTHS;
            case "years" -> ChronoUnit.YEARS;
            default -> null;
        };
    }
}
