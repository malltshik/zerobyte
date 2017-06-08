package ru.malltshik.zerobyte;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Написать на Java консольную утилиту для подсчета нулевых бит в файле.
 * В качестве аргумента командой строки утилита принимает путь к файлу.
 * Утилита должна посчитать количество нулевых бит в этом файле, а в конце работы - вывести это количество.
 * Во время работы одного экземпляра утилиты, можно запустить второй (третий и так далее) экземпляр
 * с указанием пути к тому же самому файлу.
 * В таком случае обработка файла должна быть распределена между экземплярами.
 * Когда весь файл будет обработан все экземпляры должны вывести общее кол-во нулевых бит в файле.
 *
 * Для запуска выполните в корне проекта:
 * mvn clean compile assembly:single && java -jar target/zerobyte-1.0.0-jar-with-dependencies.jar $PATH_TO_FILE
 *
 * Либо проект уже собран, то:
 * java -jar target/zerobyte-1.0.0-jar-with-dependencies.jar /tmp/test.dat
 */
public class Application {

    private static long countOfZeroBytes = 0;

    private static final String processorsPath = System.getProperty("java.io.tmpdir") +
            System.getProperty("file.separator") + "processors";

    private static final String resultsPath = System.getProperty("java.io.tmpdir") +
            System.getProperty("file.separator") + "results";

    private static String filename;

    public static void main(String[] argv) throws Exception {

        // Проверим наличия пути в аргументах
        try {
            filename = argv[0];
        } catch (ArrayIndexOutOfBoundsException e){
            System.out.println("First argument must be path to target file!");
            System.exit(255);
        }

        // Проверим что файл существует и это не директория
        File file = new File(filename);
        if(!file.exists()) {System.out.println("Invalid path to file!"); System.exit(255);}
        if(file.isDirectory()) {System.out.println("Directory is invalid target!"); System.exit(255);}

        // Таймер
        System.out.println("Processing file...");
        long start = System.currentTimeMillis();

        // Мапа для хранения результата
        ChronicleMap<String, Long> resultsMap = ChronicleMap.of(String.class, Long.class)
                .averageKey(filename)
                .entries(50_000)
                .createOrRecoverPersistedTo(new File(resultsPath), true);

        // Мапа для хранения колличества обработчиков
        ChronicleMap<String, Integer> processorsMap = ChronicleMap.of(String.class, Integer.class)
                .averageKey(filename)
                .entries(50_000)
                .createOrRecoverPersistedTo(new File(processorsPath), true);

        // Если обработчиков нет, до добавить одно обработчика и обнулить результат
        if(processorsMap.get(filename) == null || processorsMap.get(filename) == 0) {
            processorsMap.put(filename, 1);
            resultsMap.remove(filename);
        }
        // В противном случае, добавить одного обработчика и не трогать результат
        else {
            processorsMap.put(filename, processorsMap.get(filename) + 1);
        }

        // Создаем FileChannel к файлу
        FileChannel fc = new RandomAccessFile(filename, "rw").getChannel();

        // Определяем размер кусков, больше лушче, но больше Integer.MAX_VALUE нельзя, поэтому так.
        int CHUNK_SIZE = fc.size() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fc.size();

        // Будем двигать position канала и итерироватся по незалоченным кускам
        while (fc.position() < fc.size()) {

            // Пробуем залочить первый кусок файла
            FileLock lock = fc.tryLock(fc.position(), CHUNK_SIZE, false);

            // Если лока нет (уже залочен другим инстансом приложения),
            // то двигаем position и идем дальше
            if(lock == null) {
                fc.position(fc.position() + CHUNK_SIZE);
                continue;
            }

            // Если получилось залочить, то мапим этот кусок в память
            MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, fc.position(), CHUNK_SIZE);

            // Считаем нулевые биты, пока в замапленном куске есть данные
            while (mbb.hasRemaining()) {
                countOfZeroBytes += (8 - Integer.bitCount(Byte.toUnsignedInt(mbb.get())));
            }

            // Двигаем position и идем дальше.
            fc.position(fc.position() + CHUNK_SIZE);
        }

        // Добавляем результат в мапу результатов
        resultsMap.put(filename, resultsMap.get(filename) != null ?
                resultsMap.get(filename) + countOfZeroBytes : countOfZeroBytes);

        // Убираем одного обработчика
        processorsMap.put(filename, processorsMap.get(filename) != null ? processorsMap.get(filename) - 1 : 0);

        // Ждем пока обработчиков не останется
        while (processorsMap.get(filename) != 0){ Thread.sleep(1); }

        // Выводим результат
        System.out.println("Count of zero bit: " + resultsMap.get(filename));
        System.out.println("Time: " + (System.currentTimeMillis() - start) + " ms");


        /**
         * По сути программа парсит один файл и генерирует еще два. Это наверно не есть хорошо,
         * но мы получаем возможность работать с мапами, что сильно проще чем какой либо другой формат
         * типа properties или json или просто парсить строки.
         *
         * Как еще передать данные между процессами, я не придумал. Была идея с ServerSocket,
         * но ее реализация сложная и затратила бы больше времени.
         *
         * Вот как-то так :)
         */

    }

}