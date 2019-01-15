/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author Federico
 */
public class ReadTextFile {

    String path = null;
    
    public ReadTextFile(String path){
        this.path=path;
    }
    
    public String[] openFile() throws FileNotFoundException, IOException{
        
        FileReader fr = new FileReader(path);
        BufferedReader textReader = new BufferedReader(fr);
        
        int numberOfLines = countLines();
        String[] textData = new String[numberOfLines];
        
        int i=0;
        for(i=0;i<numberOfLines;i++){
            textData[i]=textReader.readLine();
        }
        textReader.close();
        return textData;
    }
    
    private int countLines() throws FileNotFoundException, IOException{
        
        FileReader file = new FileReader(path);
        BufferedReader br = new BufferedReader(file);
        
        String aLine;
        int numberOfLines=0;
        
        while((aLine=br.readLine())!=null){
            numberOfLines++;
        }
        br.close();
        return numberOfLines;
    }
    
}
