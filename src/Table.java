import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Хранит строки, итерируется по ним и сериализует в текст, первичный ключ любой таблицы - id (целое число) */
class Table implements Iterable<Row> {
    final String name;
    final List<Col> cols;
    LinkedHashMap<Integer, Row> rows = new LinkedHashMap<>();

    Table(String name, Col... cols) {
        this.name = name;
        this.cols = Arrays.asList(cols);
    }

    Col col(String name) {
        return cols.stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("нет столбца: " + name));
    }

    List<String> names() {
        return cols.stream().map(c -> c.name).collect(Collectors.toList());
    }

    @Override
    public Iterator<Row> iterator() {
        return rows.values().iterator();
    }

    /** Добавляет запись после проверки уникальности id */
    void insert(Row row) {
        Object id = row.get("id");
        if (!(id instanceof Integer)) throw new IllegalArgumentException("первичный ключ id обязателен");
        if (rows.containsKey(id)) throw new IllegalArgumentException("ключ id=" + id + " уже существует");
        rows.put((Integer) id, row);
    }

    /** Записывает строки в текстовый файл по одной записи на строку */
    void save(Path dir) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Row row : rows.values()) {
            List<String> values = new ArrayList<>();
            for (Col c : cols) values.add(escape(row.get(c.name)));
            lines.add(String.join("\t", values));
        }
        Files.write(dir.resolve(name + ".txt"), lines, StandardCharsets.UTF_8);
    }

    /** Загружает записи если файл уже существует */
    void load(Path dir) throws IOException {
        Path file = dir.resolve(name + ".txt");
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            String[] values = line.split("\t", -1);
            if (values.length != cols.size()) throw new IOException("поврежден файл " + file);
            Row row = new Row();
            for (int i = 0; i < values.length; i++) {
                String value = unescape(values[i]);
                row.put(cols.get(i).name, value == null ? null : cols.get(i).kind.parse(value, true));
            }
            insert(row);
        }
    }

    /** Экранирование чтобы можно было хранить в строках табы и переводы строк */
    static String escape(Object value) {
        if (value == null) return "\\N";
        return value.toString().replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String unescape(String s) {
        if (s.equals("\\N")) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else out.append(c);
        }
        return out.toString();
    }
}
