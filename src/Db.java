import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Класс с основной логикой (операции над записями и сохранение) */
class Db {
    final Path dir;
    final Map<String, Table> tables = new LinkedHashMap<>();
    final Table readers = new Table("readers", new Col("id", Kind.INT), new Col("name", Kind.STRING),
            new Col("birthDate", Kind.DATE), new Col("rating", Kind.DOUBLE), new Col("active", Kind.BOOL));
    final Table books = new Table("books", new Col("id", Kind.INT), new Col("title", Kind.STRING),
            new Col("readerId", Kind.INT), new Col("published", Kind.DATE),
            new Col("price", Kind.DOUBLE), new Col("available", Kind.BOOL));

    Db(Path dir) throws IOException {
        this.dir = dir;
        tables.put(readers.name, readers);
        tables.put(books.name, books);
        Files.createDirectories(dir);
        readers.load(dir);
        books.load(dir);
        validateLinks();
    }

    Table table(String name) {
        Table table = tables.get(name.toLowerCase());
        if (table == null) throw new IllegalArgumentException("нет таблицы: " + name);
        return table;
    }

    /** Проверяет внешний ключ (выданная книга должна ссылаться на читателя) */
    void validateLinks() {
        for (Row book : books) {
            Object id = book.get("readerId");
            if (id != null && !readers.rows.containsKey(id))
                throw new IllegalArgumentException("читатель id=" + id + " не существует");
        }
    }

    void insert(Table table, Row row) {
        table.insert(row);
        // чтобы неудачное добавление книги не оставляло неверную запись в памяти
        try { validateLinks(); }
        catch (RuntimeException e) { table.rows.remove(row.get("id")); throw e; }
    }

    void save() throws IOException {
        readers.save(dir);
        books.save(dir);
    }

    /** Обновляет копии строк, при ошибке старая версия таблицы остается без изменений */
    int update(Table table, String where, String assignments) {
        LinkedHashMap<Integer, Row> old = table.rows, next = new LinkedHashMap<>();
        int count = 0;
        for (Row original : old.values()) {
            Row row = original.copy();
            if (Sql.matches(table, row, where)) {
                for (String assignment : Sql.split(assignments, ',')) {
                    int eq = assignment.indexOf('=');
                    if (eq < 1) throw new IllegalArgumentException("ожидалось: столбец = значение");
                    Col col = table.col(assignment.substring(0, eq).trim());
                    row.put(col.name, col.kind.parse(assignment.substring(eq + 1), false));
                }
                count++;
            }
            Object id = row.get("id");
            if (!(id instanceof Integer) || next.containsKey(id))
                throw new IllegalArgumentException("пустой или повторяющийся первичный ключ");
            next.put((Integer) id, row);
        }
        table.rows = next;
        try { validateLinks(); }
        catch (RuntimeException e) { table.rows = old; throw e; }
        return count;
    }

    /** Удаляет записи и не дает удалить читателя за которым числятся книги */
    int delete(Table table, String where) {
        LinkedHashMap<Integer, Row> old = table.rows, next = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<Integer, Row> entry : old.entrySet()) {
            if (Sql.matches(table, entry.getValue(), where)) count++;
            else next.put(entry.getKey(), entry.getValue());
        }
        table.rows = next;
        try { validateLinks(); }
        catch (RuntimeException e) { table.rows = old; throw e; }
        return count;
    }
}
