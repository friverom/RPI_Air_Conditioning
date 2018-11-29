/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package common;

import java.util.Calendar;

/**
 * Set up an shift array of doubles with time stamp. Size of array is define in the 
 * constructor. Any new data will shift all the existing data one place.
 * @author Federico
 */
public class DataArray {
    
    private double[] data=null;
    private Calendar[] date=null;
    
    /**
     * Class Constructor. Creates the array of size. Initializes all data to 0.0
     * @param size 
     */
    public DataArray(int size){
        this.data=new double[size];
        this.date=new Calendar[size];
        
        for(int i=0;i<size;i++){
            data[i]=0.0;
            date[i]=Calendar.getInstance();
        }
    }
/**
 * Adds data to the shift array. Will shift all existing data to create space
 * for new data. Last data in array will be discarded.
 * @param t 
 */
    public synchronized void add(double t){
        
            this.shiftdata();
            data[0]=t;
            date[0]=Calendar.getInstance();
        }
  /**
   * Takes the average of the first data in the array indicated by size.
   * @param size
   * @return double average of "size" elements
   */  
    public synchronized double average(int size){
        double sum=0;
        
        for(int i=0;i<size;i++){
            sum=sum+data[i];
        }
        return sum/size;
    }
    
    /**
     * Return data from array indexed by i
     * @param i position inside the array
     * @return double data
     */
    public synchronized double getData(int i){
        return data[i];
    }
    
    /**
     * returns the time stamp of data indexed by i
     * @param i position inside array
     * @return Calendar time stamp
     */
    public synchronized Calendar getTimeStamp(int i){
        return date[i];
    }
    /**
     * Shift array one position.
     */
    private synchronized void shiftdata(){
        
        for(int i=data.length-1;i>0;i--){
            data[i]=data[i-1];
            date[i]=date[i-1];
        }
    }
    }
    
    
    
    
    