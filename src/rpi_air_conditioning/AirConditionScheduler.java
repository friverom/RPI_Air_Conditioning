/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_air_conditioning;

import java.util.Calendar;

/**
 *
 * @author Federico
 */
public class AirConditionScheduler {
    
    public static final int MINUTE=1;
    public static final int HOUR=2;
    public static final int DAY=3;
    public static final int WEEK=4;
    
    private int timer=0;
    private int times=0;
    
    Calendar date;
    
    public AirConditionScheduler(int timer, int times){
        this.timer=timer;
        this.times=times;
        this.date=calcScheduleTime();
    }

    public void setSchedule(int timer, int times) {
        this.timer = timer;
        this.times = times;
    }

    public void setTimes(int times) {
        this.times = times;
    }
    
    public AirConditionScheduler(){
    
    }
    
    public Calendar getNextChange(){
        return date;
    }
    
    public Calendar calcScheduleTime(){
       
        Calendar date=Calendar.getInstance();
        
        switch(timer){
            case 1:
                for(int i=0;i<times;i++){
                    date.add(Calendar.MINUTE, 1);
                }
                break;
                
            case 2:
                for(int i=0;i<times;i++){
                    date.add(Calendar.HOUR_OF_DAY, 1);
                }
                break;
                
            case 3:
                for(int i=0;i<times;i++){
                    date.add(Calendar.DAY_OF_MONTH, 1);
                }
                break;
                
            case 4:
                for(int i=0;i<times;i++){
                    date.add(Calendar.WEEK_OF_YEAR, 1);
                }
                break;
            default:
                
        }
    
        return date;
    }
    
    
}
