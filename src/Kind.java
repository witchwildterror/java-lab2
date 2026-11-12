import java.time.LocalDate;

/** Пять типов поддерживаемых данных */
enum Kind {
    INT, DOUBLE, STRING, DATE, BOOL;

    /** Преобразует литерал sql или значение из файла в java объект */
    Object parse(String text, boolean fromFile) {
        text = text.trim();
        if (!fromFile && text.equalsIgnoreCase("null")) return null;
        try {
            if (this == STRING) {
                if (fromFile) return text;
                if (text.length() < 2 || text.charAt(0) != '\'' || text.charAt(text.length() - 1) != '\'')
                    throw new IllegalArgumentException("строка должна быть в одинарных кавычках");
                return text.substring(1, text.length() - 1).replace("''", "'");
            }
            if (this == DATE) {
                if (!fromFile && text.startsWith("'") && text.endsWith("'"))
                    text = text.substring(1, text.length() - 1);
                return LocalDate.parse(text); // дата записывается в формате гггг-мм-дд
            }
            if (this == INT) return Integer.valueOf(text);
            if (this == DOUBLE) return Double.valueOf(text);
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException("ожидалось true или false");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("некорректное значение '" + text + "' для " + this);
        }
    }
}
