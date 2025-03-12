package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CsvReader is a utility class for reading data from CSV files into lists of maps.
 */
@Slf4j
public class CsvReader {
    /**
     * Reads all CSV files in the specified directory and its subdirectories, and returns a list of records.
     *
     * @param directoryPath The path to the directory containing CSV files.
     * @return A list of records, where each record is represented as a map of column headers to values.
     */
    public static List<Map<String, String>> readCsvFilesInDirectory(String directoryPath) {
        log.info("readCsvFilesInDirectory: read directory: " + directoryPath);
        List<Map<String, String>> records = new ArrayList<>();

        File directory = new File(directoryPath);
        processDirectory(directory, records);

        log.info("readCsvFilesInDirectory: read " + records.size() + " records");

        return records;
    }

    /**
     * Recursively processes the specified directory, searching for CSV files.
     * Adds records from CSV files to the provided list.
     *
     * @param directory The directory to process.
     * @param records   The list to which records will be added.
     */
    private static void processDirectory(File directory, List<Map<String, String>> records) {
        // Terminate recursion when we get down to a file.
        if (directory.isFile()) {
            if (directory.getName().toLowerCase().endsWith(".csv"))
                records.addAll(readCsv(directory));
            return;
        }

        File[] files = directory.listFiles();
        if (files != null)
            for (File file : files)
                processDirectory(file, records);
    }

    /**
     * Reads a CSV file and returns a list of records.
     *
     * @param file The CSV file to read.
     * @return A list of records, where each record is represented as a map of column headers to values.
     */
    public static List<Map<String, String>> readCsv(File file) {
        List<Map<String, String>> records = new ArrayList<>();

        try (Reader reader = new FileReader(file);
             CSVParser csvParser = CSVFormat.DEFAULT.withDelimiter(';').withHeader().parse(reader)) {
            log.info("Reading CSV file: " + file.getName());

            for (CSVRecord record : csvParser) {
                Map<String, String> recordMap = new HashMap<>();
                for (String header : csvParser.getHeaderMap().keySet()) {
                    recordMap.put(header, record.get(header));
                }
                records.add(recordMap);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return records;
    }
}
