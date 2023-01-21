package com.alex_zaitsev.adg.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class Writer {

    private File file;

    public Writer(File file) {
        this.file = file;
    }

    public void write(Map<String, Map<String, Integer>> dependencies) {
        try (BufferedWriter br = new BufferedWriter(new FileWriter(file))) {
            br.write("Source,Target,Weight\n");
            for (Map.Entry<String, Map<String, Integer>> entry : dependencies.entrySet()) {
                for (String dep : entry.getValue().keySet()) {
                    br.write(entry.getKey() + "," + dep + "," + entry.getValue().get(dep) + "\n");
                }
            }
            ;
        } catch (FileNotFoundException e) {
            System.err.println("Cannot found " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Cannot write " + file.getAbsolutePath());
        }
    }

}
