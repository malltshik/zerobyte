# ZEROBYTE #

Написать на Java консольную утилиту для подсчета нулевых бит в файле.
В качестве аргумента командой строки утилита принимает путь к файлу.
Утилита должна посчитать количество нулевых бит в этом файле, а в конце работы - вывести это количество.
Во время работы одного экземпляра утилиты, можно запустить второй (третий и так далее) экземпляр
с указанием пути к тому же самому файлу.
В таком случае обработка файла должна быть распределена между экземплярами.
Когда весь файл будет обработан все экземпляры должны вывести общее кол-во нулевых бит в файле.

Для запуска выполните в корне проекта:

```bash 
$ mvn clean compile assembly:single && java -jar target/zerobyte-1.0.0-jar-with-dependencies.jar $PATH_TO_FILE
```

Либо проект уже собран, то:

```bash
$ java -jar target/zerobyte-1.0.0-jar-with-dependencies.jar /tmp/test.dat
```

Создание большого файла с нулевыми байтами:

```bash
$ truncate -s 50G /tmp/test.dat
```
