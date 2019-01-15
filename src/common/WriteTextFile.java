/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author Federico
 */
public class WriteTextFile {
    
    private String path=null;
    private Boolean append_to_file = false;
    
    public WriteTextFile(String path){
        this.path=path;
    }
    
    public WriteTextFile(String path, Boolean flag){
        this.path=path;
        this.append_to_file = flag;
    }
    
    public void writeToFile(String textLine) throws IOException{
    
        FileWriter write = new FileWriter(path,append_to_file);
        PrintWriter printLine = new PrintWriter(write);
        printLine.printf("%s"+"%n", textLine);
        printLine.close();
    }
    
}
