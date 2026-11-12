import java.util.LinkedHashMap;

/** Строка таблицы хранится как словарь имя столбца - значение */
class Row extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    /** Создает копию для независимого измнения (используется в Db.update()) */
    Row copy() {
        Row row = new Row();
        row.putAll(this);
        return row;
    }
}
