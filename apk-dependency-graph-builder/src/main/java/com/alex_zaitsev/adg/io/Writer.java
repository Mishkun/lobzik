package com.alex_zaitsev.adg.io;

import java.io.*;
import java.util.Map;
import java.util.Set;

public class Writer {

    private File file;

    public Writer(File file) {
        this.file = file;
    }

    public void write(Map<String, Set<String>> dependencies) {
        try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
            br.write("Source,Target\n");
            for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
                for (String dep : entry.getValue()) {
                    br.write(entry.getKey() + "," + dep + "\n");
                }
            };
        } catch (FileNotFoundException e) {
            System.err.println("Cannot found " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Cannot write " + file.getAbsolutePath());
        }
    }

}
