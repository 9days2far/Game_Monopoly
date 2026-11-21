package ProjectOop.web;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bộ serialize/deserialize JSON thuần Java (không phụ thuộc thư viện ngoài).
 */
public final class JsonUtil {

    private JsonUtil() {}

    // =========================================================================
    // SERIALIZER
    // =========================================================================

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder(256);
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
            return;
        }
        if (value instanceof Number num) {
            if (num instanceof Double d && !Double.isFinite(d)) {
                sb.append("null");
            } else if (num instanceof Float f && !Float.isFinite(f)) {
                sb.append("null");
            } else {
                sb.append(num);
            }
            return;
        }
        if (value instanceof Enum<?> e) {
            appendEscapedString(sb, e.name());
            return;
        }
        if (value instanceof CharSequence cs) {
            appendEscapedString(sb, cs.toString());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendEscapedString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                appendJson(sb, entry.getValue());
            }
            sb.append('}');
            return;
        }
        if (value instanceof Iterable<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                appendJson(sb, item);
            }
            sb.append(']');
            return;
        }
        if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                appendJson(sb, Array.get(value, i));
            }
            sb.append(']');
            return;
        }
        appendEscapedString(sb, value.toString());
    }

    private static void appendEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // =========================================================================
    // PARSER
    // =========================================================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Parser parser = new Parser(json);
        Object val = parser.parseRoot();
        if (!(val instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("JSON payload phải là một object {...}.");
        }
        return (Map<String, Object>) val;
    }

    public static Object parseValue(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Chuỗi JSON rỗng.");
        }
        return new Parser(json).parseRoot();
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        Object parseRoot() {
            skipWhitespace();
            Object val = readValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Ký tự dư ở cuối JSON tại vị trí " + pos);
            }
            return val;
        }

        private Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Kết thúc JSON bất ngờ tại vị trí " + pos);
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw new IllegalArgumentException("Ký tự không hợp lệ '" + c + "' tại vị trí " + pos);
                }
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("Thiếu key chuỗi tại vị trí " + pos);
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                Object val = readValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Thiếu ',' hoặc '}' tại vị trí " + pos);
                }
            }
            return map;
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Thiếu ',' hoặc ']' tại vị trí " + pos);
                }
            }
            return list;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("Escape không hoàn chỉnh trong chuỗi JSON.");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Mã Unicode \\uXXXX không hợp lệ.");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Escape không hỗ trợ: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Chuỗi JSON chưa được đóng bằng dấu '\"'.");
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Boolean không hợp lệ tại vị trí " + pos);
        }

        private Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Literal không hợp lệ tại vị trí " + pos);
        }

        private Number readNumber() {
            int start = pos;
            if (peek('-')) pos++;
            boolean isFloat = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    if (c == '-' || c == '+') {
                        char prev = src.charAt(pos - 1);
                        if (prev != 'e' && prev != 'E') {
                            break;
                        }
                    }
                    isFloat = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private boolean peek(char expected) {
            return pos < src.length() && src.charAt(pos) == expected;
        }

        private void expect(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Mong đợi '" + expected + "' tại vị trí " + pos);
            }
            pos++;
        }
    }

    // =========================================================================
    // HELPERS ĐỌC MAP AN TOÀN
    // =========================================================================

    public static String getString(Map<String, Object> map, String key, String defaultVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultVal;
        }
        return String.valueOf(map.get(key));
    }

    public static int getInt(Map<String, Object> map, String key, int defaultVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultVal;
        }
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultVal;
        }
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    public static List<Integer> getIntList(Map<String, Object> map, String key) {
        List<Integer> result = new ArrayList<>();
        if (map == null || !map.containsKey(key) || !(map.get(key) instanceof Collection<?> col)) {
            return result;
        }
        for (Object item : col) {
            if (item instanceof Number n) {
                result.add(n.intValue());
            } else if (item != null) {
                try {
                    result.add(Integer.parseInt(String.valueOf(item).trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }
}
