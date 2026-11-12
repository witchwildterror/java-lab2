import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/** Запуск: {@code java Main [папка с данными]} */
public class Main {
    /** Загружает базу и читает команды */
    public static void main(String[] args) {
        Db db;
        try { db = new Db(Paths.get(args.length == 0 ? "data" : args[0])); }
        catch (Exception e) { System.err.println("Ошибка загрузки: " + e.getMessage()); return; }
        System.out.println("Введите команду (exit чтобы выйти)");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                if (System.console() != null) System.out.print("db> ");
                String line = in.readLine();
                if (line == null || line.trim().equalsIgnoreCase("exit")) break;
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                try { System.out.println(Sql.execute(db, line)); }
                catch (Exception e) { System.out.println("Ошибка: " + e.getMessage()); }
            }
            db.save();
        } catch (IOException e) { System.err.println("Ошибка: " + e.getMessage()); }
    }
}
