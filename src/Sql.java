import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/** Отвечает за обработку и выполнение sql команд */
class Sql {
    static final Pattern INSERT = Pattern.compile("INSERT\\s+INTO\\s+(\\w+)(?:\\s*\\(([^)]*)\\))?\\s+VALUES\\s*(.+)", Pattern.CASE_INSENSITIVE);
    static final Pattern SELECT = Pattern.compile("SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    static final Pattern UPDATE = Pattern.compile("UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    static final Pattern DELETE = Pattern.compile("DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    static final Pattern CONDITION = Pattern.compile("(\\w+)\\s*(>=|<=|!=|=|>|<)\\s*(.+)");

    /** Выполняет одну команду и возвращает результат */
    static String execute(Db db, String sql) throws IOException {
        sql = sql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        Matcher matcher = INSERT.matcher(sql);
        if (matcher.matches()) return insert(db, matcher);
        matcher = SELECT.matcher(sql);
        if (matcher.matches()) return select(db, matcher);
        matcher = UPDATE.matcher(sql);
        if (matcher.matches()) {
            int count = db.update(db.table(matcher.group(1)), matcher.group(3), matcher.group(2));
            db.save();
            return "Обновлено записей: " + count;
        }
        matcher = DELETE.matcher(sql);
        if (matcher.matches()) {
            int count = db.delete(db.table(matcher.group(1)), matcher.group(2));
            db.save();
            return "Удалено записей: " + count;
        }
        throw new IllegalArgumentException("неизвестная или некорректная команда");
    }

    /** Добавляет одну или несколько записей */
    private static String insert(Db db, Matcher matcher) throws IOException {
        Table table = db.table(matcher.group(1));
        List<String> names = matcher.group(2) == null ? table.names() : split(matcher.group(2), ',');
        int count = 0;
        LinkedHashMap<Integer, Row> old = table.rows;
        table.rows = new LinkedHashMap<>();
        old.forEach((id, row) -> table.rows.put(id, row.copy()));
        try {
            for (String tuple : split(matcher.group(3), ',')) {
                tuple = tuple.trim();
                if (!tuple.startsWith("(") || !tuple.endsWith(")"))
                    throw new IllegalArgumentException("ожидались значения в скобках");
                List<String> values = split(tuple.substring(1, tuple.length() - 1), ',');
                if (names.size() != values.size())
                    throw new IllegalArgumentException("число столбцов и значений различается");
                Row row = new Row();
                for (String name : table.names()) row.put(name, null);
                for (int i = 0; i < names.size(); i++) {
                    Col col = table.col(names.get(i).trim());
                    row.put(col.name, col.kind.parse(values.get(i), false));
                }
                db.insert(table, row);
                count++;
            }
            db.save();
            return "Добавлено записей: " + count;
        } catch (RuntimeException | IOException e) {
            table.rows = old;
            throw e;
        }
    }

    /** Передает найденные записи функции форматирования */
    private static String select(Db db, Matcher matcher) {
        Table table = db.table(matcher.group(2));
        List<String> names = matcher.group(1).trim().equals("*") ? table.names() : split(matcher.group(1), ',');
        List<Row> result = new ArrayList<>();
        for (Row row : table) if (matches(table, row, matcher.group(3))) result.add(row);
        return format(table, names, result);
    }

    /** Проверяет условие WHERE */
    static boolean matches(Table table, Row row, String where) {
        if (where == null) return true;
        Matcher matcher = CONDITION.matcher(where.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("поддерживается только одно условие WHERE");
        Col col = table.col(matcher.group(1));
        Object left = row.get(col.name), right = col.kind.parse(matcher.group(3), false);
        String operator = matcher.group(2);
        if (operator.equals("=")) return Objects.equals(left, right);
        if (operator.equals("!=")) return !Objects.equals(left, right);
        if (left == null || right == null) return false;
        int comparison;
        if (left instanceof Number) comparison = Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        else if (left instanceof Comparable) comparison = ((Comparable) left).compareTo(right);
        else throw new IllegalArgumentException("значения нельзя сравнить");
        return (operator.equals(">") ? comparison > 0 : (operator.equals("<") ? comparison < 0 : (operator.equals(">=") ? comparison >= 0 : comparison <= 0)));
    }

    /** Делит список по запятым без кавычек и скобок */
    static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quote = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                // внутри строки '' означает одну кавычку и не завершает строку
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    part.append("''");
                    i++;
                } else {
                    quote = !quote;
                    part.append(c);
                }
            } else if (!quote && c == '(') { depth++; part.append(c); }
            else if (!quote && c == ')') { depth--; part.append(c); }
            else if (!quote && depth == 0 && c == separator) {
                parts.add(part.toString().trim());
                part.setLength(0);
            } else part.append(c);
        }
        parts.add(part.toString().trim());
        return parts;
    }

    /** Формирует результат SELECT в виде таблицы */
    static String format(Table table, List<String> requested, List<Row> rows) {
        List<Col> cols = requested.stream().map(s -> table.col(s.trim())).collect(Collectors.toList());
        List<List<String>> values = rows.stream().map(row -> cols.stream()
                .map(col -> row.get(col.name) == null ? "NULL" : row.get(col.name).toString())
                .collect(Collectors.toList())).collect(Collectors.toList());
        int[] widths = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) widths[i] = cols.get(i).name.length();
        for (List<String> row : values)
            for (int i = 0; i < row.size(); i++) widths[i] = Math.max(widths[i], row.get(i).length());
        StringBuilder border = new StringBuilder("+");
        for (int width : widths) border.append("-".repeat(width + 2)).append("+");
        StringBuilder out = new StringBuilder(border).append('\n');
        appendLine(out, cols.stream().map(col -> col.name).collect(Collectors.toList()), widths);
        out.append(border).append('\n');
        for (List<String> row : values) appendLine(out, row, widths);
        out.append(border).append("\nСтрок: ").append(rows.size());
        return out.toString();
    }

    private static void appendLine(StringBuilder out, List<String> values, int[] widths) {
        out.append('|');
        for (int i = 0; i < values.size(); i++)
            out.append(' ').append(String.format("%-" + widths[i] + "s", values.get(i))).append(" |");
        out.append('\n');
    }
}
