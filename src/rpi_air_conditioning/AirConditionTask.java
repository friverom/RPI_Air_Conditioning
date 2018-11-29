/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_air_conditioning;

import common.DataArray;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpio_client.Net_RPI_IO;

/**
 *
 * @author Federico
 */
public class AirConditionTask {
    
    private static final int AUTO = 4; //Automatic selector input port
    private static final int RESET = 5; //Reset Alarm input port
    private static final int R_AC1 = 6; //Select AC1 output relay
    private static final int R_AC2 = 7; //Select AC2 output relay
    private static final int ALARM = 8; //Alarm relay
    private static final int TEMP = 1; //Temperature sensor input
    private static final int ACTASK = 4;
    private static final int TASKLEVEL = 1;
    
    Net_RPI_IO rpio = null;
    private String address = "localhost";
    
    DataArray temperature = new DataArray(60);
    DataArray avg_temp = new DataArray(1440);
    AirConditionScheduler schedule = new AirConditionScheduler();
   
    Calendar nextDate;
    long start_date;
    boolean auto;
    double alarm = 26.0; //Alarm Temp
    double alfa = 0.61; // dT/(dT+RC) for filter
    boolean alarm_flag = false;
    long ac1_timer=0;
    long ac2_timer=0;
    long ac_last=0;
       
    static int timer=0; //internal count for 1 minute for average
    static int state=0; //Air Condition state
    static int runState=0; //Auto/Man state
    static boolean schedule_flag=false;
    private boolean runFlag = true;
    private int schedule_timer = 1;
    
    private boolean sim_flag = false;
    private double sim_temp = 22.0;
    
    public AirConditionTask() {
        long[] log=new long[2];
        this.address="localhost";
        rpio = new Net_RPI_IO(this.address,30000);
        log=readAClog();
        ac1_timer=log[0];
        ac2_timer=log[1];
        start_date=log[2];
        ac_last=start_date;
    }
    
    public AirConditionTask(String address){
        long[] log=new long[2];
        this.address=address;
        rpio = new Net_RPI_IO(this.address,30000);
        log=readAClog();
        ac1_timer=log[0];
        ac2_timer=log[1];
        start_date=log[2];
        ac_last=start_date;
    }
    
    public String start(){
        Thread ac_task = new Thread(new AcTask(),"Aircondition Task");
        ac_task.start();
        return "Started";
    }
    
    public String killThread(){
        runFlag=false;
        return "Killed";
    }
    
    public String setAlarmTemp(double temp){
        this.alarm=temp;
        return "Alarm set";
    }
    
    public String setFilter(double rc){
        this.alfa=rc;
        return "RC Filter set";
    }
    public String setSimFlag(){
        this.sim_flag=true;
        return "Sim mode active";
    }
    
    public String resetSimFlag(){
        this.sim_flag=false;
        return "Temp mode active";
    }
    
    public String setSimTemp(double temp){
        this.sim_temp=temp;
        return "Temp Set";
    }
    
    public String setScheduleTimer(int timer){
        this.schedule_timer=timer;
        schedule.setSchedule(AirConditionScheduler.MINUTE, schedule_timer);
        nextDate = schedule.calcScheduleTime();
        return "Schedule set";
    }
    
    public String getTemperature(){
        String temp=String.format("%.2f", temperature.average(60));
        return temp;
    }
    
    public String getStatus(){
        String report;
        String temp = String.format("%.2f", temperature.average(60));
        SimpleDateFormat format1 = new SimpleDateFormat("dd/MM/yyyy 'at' HH:mm zzz");

         if(auto){
                    report="AC System mode: AUTOMATIC\n";
                }
                else{
                    report="AC System mode: MANUAL\n";
                }
         
        switch(state){
            case 0:
                report=report+"AC #1: RUNNING\n";
                report=report+"Actual room temperature: "+temp+"\n";
                report=report+"AC next switchover: "+format1.format(nextDate.getTime());
                break;
                
            case 1:
                report=report+"AC #2: RUNNING\n";
                report=report+"Actual room temperature: "+temp+"\n";
                report=report+"AC next switchover: "+format1.format(nextDate.getTime());
                break;
                
            case 2:
                report=report+"AC #1: FAIL\n";
                report=report+"AC #2: RUNNING\n";
                report=report+"Actual room temperature: "+temp+"\n";
                break;
                
            case 3:
                report=report+"AC #2: FAIL\n";
                report=report+"AC #1: RUNNING\n";
                report=report+"Actual room temperature: "+temp+"\n";
                break;
        }
        String resp="\nRunning hours since "+format1.format(start_date)+"\n";
        resp=resp+"AC #1: "+String.format("%.1f", ac1_timer/(1000.0*3600.0))+" hrs\n";
        resp=resp+"AC #2: "+String.format("%.1f", ac2_timer/(1000.0*3600.0))+" hrs\n";
        return report+resp;
    }
    
    public String resetAlarm(){
        String reset="";
        String temp="";
        
        if(!alarm_flag){
            switch(state){
                
                case 2:
                    setAirCondition(1);
                    rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
                    state=0; // Switch to state 0 if reset and no alarm present
                    reset = "System Reset. AC#1 Running";
                    break;
                    
                case 3:
                    setAirCondition(2);
                    rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
                    state = 1; // Switch to state 1 if reset and no alarm present
                    reset = "System Reset. AC#2 Running";
                    break;
                    
                default:
                    reset="System running normally. NO need to reset\n";
            }
        }
        return reset;
    }
    
    public String alarmAck(){
        rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
        
        return "AC alarm Acknowledged";
    }
    
    public String getTempLog(int sampletime) {
        
        String data ="";
        data=String.format("%.2f,", avg_temp.getData(0));
        
        for(int i=sampletime; i<1440; i+=sampletime){
            data=data+String.format("%.2f,", avg_temp.getData(i));
        }
        return data;
    }
    
    public class AcTask implements Runnable{

        @Override
        public void run() {

            schedule.setSchedule(AirConditionScheduler.MINUTE, schedule_timer);
            nextDate = schedule.calcScheduleTime();
            rpio.setLock(ACTASK, TASKLEVEL, R_AC1);
            rpio.setLock(ACTASK, TASKLEVEL, R_AC2);
            rpio.setLock(ACTASK, TASKLEVEL, ALARM);
            
            if (ac1_timer > ac2_timer) {
                state = 1;
            }
            
            while (runFlag) {
            processTemp();
            alarm_flag=checkTempAlarm();
            schedule_flag=checkDateChange();
            
            if(get_input(AUTO)){
                if(state==0){
                    setAirCondition(1);
                }
                try {
                    check_state(); //if Auto run schedule
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(AcTask.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(AcTask.class.getName()).log(Level.SEVERE, null, ex);
                }
                auto=true;
            } else {
                state=0;
                setAirCondition(0); //if Man turn off AC's
                auto=false;
            }
            try {
                Thread.sleep(1000); //Sample Temp every second
            } catch (InterruptedException ex) {
                Logger.getLogger(RPI_Air_Conditioning.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        }
    
    }
    
    private void setAirCondition(int i){
        
        switch(i){
            case 0:
                rpio.resetRly(ACTASK,TASKLEVEL,R_AC1);
                rpio.resetRly(ACTASK,TASKLEVEL,R_AC2);
                break;
            case 1:
                rpio.resetRly(ACTASK,TASKLEVEL,R_AC2);
                rpio.setRly(ACTASK,TASKLEVEL,R_AC1);
                break;
            case 2:
                rpio.resetRly(ACTASK,TASKLEVEL,R_AC1);
                rpio.setRly(ACTASK,TASKLEVEL,R_AC2);
                break;
            default:
                
        }
    }
    
    private void createAClog() throws FileNotFoundException, UnsupportedEncodingException{
        PrintWriter writer = new PrintWriter("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt", "UTF-8");
      //  PrintWriter writer = new PrintWriter("ac_log.txt", "UTF-8");
        writer.println("Running hours,"+ac1_timer+","+ac2_timer+","+System.currentTimeMillis()+"\n");
        writer.close();
    }
    
    private long[] readAClog(){
        Scanner scanner;
        long[] log = new long[3];
        log[0]=0;
        log[1]=0;
        log[2]=0;
        
        
        try {
            scanner = new Scanner(new File("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt"));
         //   scanner = new Scanner(new File("ac_log.txt"));
            String text = scanner.useDelimiter("\n").next();
            scanner.close(); // Put this call in a finally block

            String[] parts = text.split(",");
            
            log[0] = Long.parseLong(parts[1]);
            log[1] = Long.parseLong(parts[2]);
            log[2] = Long.parseLong(parts[3]);
            
        } catch (FileNotFoundException ex) {
            PrintWriter writer;
            try {
                writer = new PrintWriter("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt", "UTF-8");
             //   writer = new PrintWriter("ac_log.txt", "UTF-8");
                writer.println("Running hours," + 0 + "," + 0 + ","+System.currentTimeMillis()+"\n");
                writer.close();
            } catch (FileNotFoundException ex1) {
                Logger.getLogger(RPI_Air_Conditioning.class.getName()).log(Level.SEVERE, null, ex1);
            } catch (UnsupportedEncodingException ex1) {
                Logger.getLogger(RPI_Air_Conditioning.class.getName()).log(Level.SEVERE, null, ex1);
            }
           
        }
       return log; 
    }
     private void check_state() throws FileNotFoundException, UnsupportedEncodingException{
    
        switch(state){
        
            //State 0. System in auto an AC#1 running. No alarm
            case 0:
                if (schedule_flag) {
                    setAirCondition(2);
                    createAClog();
                    schedule_flag = false;
                    state = 1; //Switch to state 1 if schedule signal
                    
                    
                } else if (alarm_flag) {
                    setAirCondition(2);
                    rpio.setRly(ACTASK,TASKLEVEL,ALARM);
                    createAClog();
                    state = 2; //AC #1 in alarm switch to state 2
                    
                }
                ac1_timer=ac1_timer+System.currentTimeMillis()-ac_last;
                ac_last=System.currentTimeMillis();
              //  setAirCondition(1);
                break;
            //State 1. System in Auto an AC#2 running. No alarm    
            case 1:
                if(schedule_flag){
                    setAirCondition(1);
                    schedule_flag=false;
                    createAClog();
                    state=0; //Switch to state 0 if schedula signal
                    
                    
                }else if(alarm_flag){
                    setAirCondition(1);
                    rpio.setRly(ACTASK,TASKLEVEL,ALARM);
                    createAClog();
                    state=3; // AC #2 in alarm, switch to state 3
                    
                    
                }
                ac2_timer=ac2_timer+System.currentTimeMillis()-ac_last;
                ac_last=System.currentTimeMillis();
              //  setAirCondition(2);
                break;
            //AC #1 in alarm. AC #2 running. No reset signal    
            case 2:
                if(get_input(RESET) && !alarm_flag){
                    setAirCondition(1);
                    createAClog();
                    rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
                    state=0; // Switch to state 0 if reset and no alarm present
                    
                    
                }
                ac2_timer=ac2_timer+System.currentTimeMillis()-ac_last;
                ac_last=System.currentTimeMillis();
             //   setAirCondition(2);
                break;
                
            case 3:
                if (get_input(RESET) && !alarm_flag) {
                    setAirCondition(2);
                    createAClog();
                    rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
                    state = 1; // Switch to state 1 if reset and no alarm present
                    
                    
                }
                ac1_timer=ac1_timer+System.currentTimeMillis()-ac_last;
                ac_last=System.currentTimeMillis();
              //  setAirCondition(1);
                break;
            default:
        }
    }
     
    private boolean checkDateChange(){
        
        Calendar date=Calendar.getInstance();
        if(date.getTimeInMillis()>nextDate.getTimeInMillis()){
            nextDate=schedule.calcScheduleTime();
            
         /*   System.out.println("Actual Time "+date.getTime()+" Mills "+date.getTimeInMillis());
            System.out.println("Next Change "+nextDate.getTime()+" Mills "+nextDate.getTimeInMillis());*/
           // System.out.format("Temp %.2f%n",data.temp.average(60));
            return true;
        } else {
            return false;
        }
        
    }
    
    private void processTemp(){
    
            temperature.add(filter(getTemp()));
            timer = timer + 1;
            if (timer > 60) {
                avg_temp.add(temperature.average(60));
                timer = 0;
            }
    }
    
    private boolean checkTempAlarm(){
        
        double roomTemp=temperature.average(60);
        
        if (roomTemp > alarm) {
         //   alarm_flag = true;
            return true;
        }
        else if(roomTemp<25.0){
                   
            return false;
        } else {
            return alarm_flag;
        }
    }
    
    private double getTemp(){
        double temp=0.0;
        
        if(!sim_flag){
        String read=rpio.readAnalogChannel(ACTASK, TASKLEVEL, TEMP);
        String parts[]=read.split(",");
        if(parts.length!=3){
            return temp;
        }
        double voltage = Double.parseDouble(parts[2]);
        //double analog=(double)value/4096*5;
        //double temp=18.752*analog-36.616;
       // double analog=(double)value/4096*4.096; 0..20mA sensor
        temp=22.965*voltage-41.427;
       // System.out.format("Temp %.2f%n", temp);
        return temp;
        } else {
            return sim_temp;
        }
    }
    
    private double filter(double t){
        
        double temp=t*alfa+temperature.getData(0)*(1-alfa);
        return temp;
    }
    private boolean get_input(int port){
        String resp = rpio.getInput(ACTASK, TASKLEVEL, port);
        String parts[] = resp.split(",");
        boolean status;
        if(parts.length==3){
            status=Boolean.parseBoolean(parts[2]);
        } else {
            status = true;
        }
        return status;
    }
     
     
}


