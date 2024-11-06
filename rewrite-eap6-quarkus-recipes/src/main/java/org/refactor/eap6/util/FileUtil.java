package org.refactor.eap6.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.openrewrite.SourceFile;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public class FileUtil {
    public String readPathFileContent(String fileName) throws IOException {
        if (fileName == null || "".equals(fileName)) {
            return "";
        }
        return readFileContent(new InputStreamReader(Files.newInputStream(Paths.get(fileName))));
    }

    public boolean fileAlreadyExist(String fileName) {
        return Files.exists(Paths.get(fileName));
    }

    public String readResourceFileContent(String fileName) throws IOException {
        if (fileName == null || "".equals(fileName)) {
            return "";
        }
        return readFileContent(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(fileName)));
    }

    public String readFileContent(InputStreamReader inputStreamReader) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader bufferReader = new BufferedReader(inputStreamReader)) {
            int c;
            char[] cBuf = new char[1024];
            while ((c = bufferReader.read(cBuf)) != -1) {
                sb.append(cBuf, 0, c);
            }
        }
        return sb.toString();
    }


    public PlainText createNewFile(String relativeFilePath, String fileContents) {
        PlainTextParser parser = new PlainTextParser();
        SourceFile sourceFile = parser.parse(fileContents).findFirst().get();
        return sourceFile.withSourcePath(Paths.get(relativeFilePath));
    }

    public void createFile(final String filename, final String content) {
        try {
            File file = new File(filename);
            Files.createDirectories(Paths.get(file.getParent()));

            FileOutputStream os = new FileOutputStream(file);
            try (BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(os))) {
                bufferWriter.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addToStatistics(String recipe, String targetClass, Duration duration) {
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(Paths.get("statistics.csv"), StandardOpenOption.APPEND, StandardOpenOption.CREATE), CSVFormat.DEFAULT)) {
            printer.printRecord(recipe, targetClass, duration.getSeconds());
        } catch(IOException ioe) {
            System.out.println(ioe.getMessage());
        }
    }
}
