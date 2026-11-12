### Файлы
- `Main.java` - консольный интерфейс
- `Kind.java` - типы данных и преобразование значений
- `Col.java` - описание столбца
- `Row.java` - запись таблицы
- `Table.java` - хранение и сериализация таблицы
- `Db.java` - схема базы данных, ключи и операции
- `Sql.java` - обработка запросов

### Таблица `readers` - читатели
| Столбец | Тип | Назначение |
|---|---|---|
| `id` | целое число | первичный ключ |
| `name` | строка | имя читателя |
| `birthDate` | дата | дата рождения, `гггг-мм-дд` |
| `rating` | число с плавающей точкой | рейтинг |
| `active` | логическое значение | активен ли читатель |

### Таблица `books` - книги
| Столбец | Тип | Назначение |
|---|---|---|
| `id` | целое число | первичный ключ |
| `title` | строка | название книги |
| `readerId` | целое число или `NULL` | внешний ключ (`readers.id`) |
| `published` | дата | дата публикации |
| `price` | число с плавающей точкой | цена |
| `available` | логическое значение | доступна ли книга |

### Диаграмма классов
```mermaid
classDiagram
    direction LR

    class Main {
        +main(String[] args) void$
    }

    class Db {
        ~Path dir
        ~Map tables
        ~Table readers
        ~Table books
        ~table(String name) Table
        ~insert(Table table, Row row) void
        ~update(Table table, String where, String assignments) int
        ~delete(Table table, String where) int
        ~validateLinks() void
        ~save() void
    }

    class Table {
        ~String name
        ~List cols
        ~LinkedHashMap rows
        ~col(String name) Col
        ~names() List
        ~insert(Row row) void
        ~load(Path dir) void
        ~save(Path dir) void
        +iterator() Iterator
        ~escape(Object value) String$
        ~unescape(String value) String$
    }

    class Row {
        -long serialVersionUID$
        ~copy() Row
    }

    class Col {
        ~String name
        ~Kind kind
    }

    class Kind {
        <<enum>>
        INT
        DOUBLE
        STRING
        DATE
        BOOL
        ~parse(String text, boolean fromFile) Object
    }

    class Sql {
        ~execute(Db db, String sql) String$
        -insert(Db db, Matcher matcher) String$
        -select(Db db, Matcher matcher) String$
        ~matches(Table table, Row row, String where) boolean$
        ~split(String text, char separator) List$
        ~format(Table table, List requested, List rows) String$
    }

    class Iterable {
        <<interface>>
        +iterator() Iterator
    }

    class LinkedHashMap {
        <<java api>>
    }

    Main --> Db : создает и загружает
    Main ..> Sql : передает SQL команды

    Db "1" *-- "2" Table : readers и books
    Table "1" *-- "*" Col : содержит схему
    Table "1" o-- "*" Row : хранит записи
    Col --> Kind : задает тип данных

    Db ..> Sql : проверяет условия WHERE
    Sql ..> Db : выполняет операции

    Table ..|> Iterable : реализует
    Row --|> LinkedHashMap : наследует
```

### Хранение данных
По умолчанию создается каталог `data` с файлами `readers.txt` и `books.txt`.
Каждая строка файлов - одна запись, значения разделены символом табуляции. `NULL` записывается как `\N`, обратный слэш, табуляция и перевод строки экранируются. Даты хранятся в формате `гггг-мм-дд`.
После каждой успешной команды данные сохраняются. При запуске существующие файлы загружаются автоматически.

### Запуск
В корне проекта выполнить:
```bash
javac -d out src/*.java
java -cp out Main
```

### Примеры команд
#### Вставка
```sql
INSERT INTO readers VALUES (1, 'Иван Петров', '2000-05-10', 4.5, TRUE);
INSERT INTO books VALUES (101, 'Война и мир', 1, '1869-01-01', 1200.0, TRUE);
INSERT INTO books VALUES (102, '1984', NULL, '1949-06-08', 650.0, TRUE), (103, 'Маленький принц', 1, '1943-04-06', 500.0, TRUE);
```

#### Выбор
```sql
SELECT * FROM readers;
SELECT name, rating FROM readers WHERE rating >= 4.5;
SELECT title, price FROM books WHERE price < 800;
SELECT * FROM books WHERE readerId = 1;
```

#### Обновление
```sql
UPDATE readers SET rating = 4.9 WHERE id = 1;
UPDATE books SET price = 900.0, available = FALSE WHERE id = 102;
```

#### Удаление
```sql
DELETE FROM books WHERE title = '1984';
DELETE FROM readers WHERE id = 3;
```

Правила синтаксиса:

- регистр ключевых слов, имен таблиц и столбцов не важен
- строки заключаются в одинарные кавычки, даты можно также заключать в кавычки
- внутри строки одинарная кавычка записывается как `''`
- поддерживается одно условие `WHERE` с операторами `=`, `!=`, `>`, `<`, `>=`, `<=`
- без `WHERE` операция применяется ко всем записям
- точка с запятой в конце необязательна
