/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_air_conditioning;

import common.DataArray;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpio_client.Net_RPI_IO;
import util.ReadTextFile;
import util.WriteTextFile;

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
    
    public AirConditionTask() throws IOException {
        long[] log=new long[2];
        this.address="localhost";
        rpio = new Net_RPI_IO(this.address,30000);
        log=readAClog();
        ac1_timer=log[0];
        ac2_timer=log[1];
        start_date=log[2];
        ac_last=start_date;
        readSettings();
    }
    
    public AirConditionTask(String address) throws IOException{
        long[] log=new long[2];
        this.address=address;
        rpio = new Net_RPI_IO(this.address,30000);
        log=readAClog();
        ac1_timer=log[0];
        ac2_timer=log[1];
        start_date=log[2];
        ac_last=start_date;
        readSettings();
    }
    
    public String start(){
        Thread ac_task = new Thread(new AcTask(),"Aircondition Task");
        ac_task.start();
        return "Started";
    }
    
    public String killThread() throws IOException{
        saveSettings();
        runFlag=false;
        return "Killed";
    }
    
    public String setAlarmTemp(double temp) throws IOException{
        this.alarm=temp;
        saveSettings();
        return "Alarm set";
    }
    
    public String getAlarmTemp(){
        String temp=String.format("%.2f", this.alarm);
        return temp;
    }
    
    public String setFilter(double rc){
        this.alfa=rc;
        return "RC Filter set";
    }
    public String setSimFlag() throws IOException{
        this.sim_flag=true;
        saveSettings();
        return "Sim mode active";
    }
    
    public String resetSimFlag(){
        this.sim_flag=false;
        return "Temp mode active";
    }
    
    public String setSimTemp(double temp) throws IOException{
        this.sim_temp=temp;
        saveSettings();
        return "Temp Set";
    }
    
    public String getSimTemp(){
        String temp=String.format("%.2f", this.sim_temp);
        return temp;
    }
    public String setScheduleTimer(int timer) throws IOException{
        this.schedule_timer=timer;
        schedule.setSchedule(AirConditionScheduler.MINUTE, schedule_timer);
        nextDate = schedule.calcScheduleTime();
        saveSettings();
        return "Schedule set";
    }
    
    public String getScheduleTimer(){
        return ""+this.schedule_timer;
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
        if(sim_flag){
            report=report+"Temperature simulation mode ACTIVE\n";
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
        }else{
            reset="System in ALARM. Cannot reset";
        }
        return reset;
    }
    
    public String alarmAck(){
        rpio.resetRly(ACTASK,TASKLEVEL,ALARM);
        
        return "AC alarm Acknowledged";
    }
    
    public String getTempLog(int sampletime) throws IOException {
        
        String path = "/home/pi/NetBeansProjects/RPI_Air_Conditioning/templog"+sampletime+".txt";
        File file = new File(path);
                
        if(!file.exists()){
            file.createNewFile();
        }
        
        WriteTextFile templog = new WriteTextFile(path,false);
        String data ="";
        data=String.format("%.2f,\n", avg_temp.getData(0));
        templog.writeToFile(data);
        
        for(int i=sampletime; i<1440; i+=sampletime){
            data=data+String.format("%.2f, \n", avg_temp.getData(i));
            templog.writeToFile(data);
        }
        return path;
    }
    
    public class AcTask implements Runnable{

        @Override
        public void run() {

            schedule.setSchedule(AirConditionScheduler.MINUTE, schedule_timer);
            nextDate = schedule.calcScheduleTime();
            rpio.setLock(ACTASK, TASKLEVEL, R_AC1);
            rpio.setLock(ACTASK, TASKLEVEL, R_AC2);
            rpio.setLock(ACTASK, TASKLEVEL, ALARM);
            rpio.setAnalogSettings(ACTASK, TASKLEVEL, TEMP, "20;0.0;1.0");
            
            if (ac1_timer > ac2_timer) {
                state = 1;
            }else{
                state = 0;
            }
           
            while (runFlag) {
            processTemp();
            alarm_flag=checkTempAlarm();
            schedule_flag=checkDateChange();
            
            if(get_input(AUTO)){
                if(state==0){
                    setAirCondition(1);
                }else if (state==1){
                    setAirCondition(2);
                }
                try {
                    check_state(); //if Auto run schedule
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(AcTask.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(AcTask.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(AirConditionTask.class.getName()).log(Level.SEVERE, null, ex);
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
        rpio.resetRly(ACTASK, TASKLEVEL, R_AC1);
        rpio.resetRly(ACTASK, TASKLEVEL, R_AC2);
        rpio.resetRly(ACTASK, TASKLEVEL, ALARM);
        rpio.releaseLock(ACTASK, TASKLEVEL, R_AC1);
        rpio.releaseLock(ACTASK, TASKLEVEL, R_AC2);
        rpio.releaseLock(ACTASK, TASKLEVEL, ALARM);
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
    
    private void createAClog() throws FileNotFoundException, UnsupportedEncodingException, IOException{
        
        File file = new File("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt");
        if(!file.exists()){
            WriteTextFile write = new WriteTextFile("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt",false);
            String text = String.format("Running hours,%d,%d,%d", ac1_timer,ac2_timer,System.currentTimeMillis());
            write.writeToFile(text);
        } else {
            ReadTextFile read = new ReadTextFile("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt");
            String[] lines = read.openFile();
            String parts[] = lines[0].split(",");
            String data = String.format("Running hours,%d,%d,%d", ac1_timer,ac2_timer,Long.parseLong(parts[3]));
            WriteTextFile write = new WriteTextFile("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt",false);
            write.writeToFile(data);
        }
        
     /*   File file = new File("/home/pi/NetBeansProjects/RPI_Air_Conditioning/ac_log.txt");
        file.getParentFile().mkdirs();
        PrintWriter writer = new PrintWriter(file, "UTF-8");
      //  PrintWriter writer = new PrintWriter("ac_log.txt", "UTF-8");
        writer.println("Running hours,"+ac1_timer+","+ac2_timer+","+System.currentTimeMillis()+"\n");
        writer.close();*/
    }
    
    private long[] readAClog() throws IOException{
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
            ac1_timer=0;
            ac2_timer=0;
            log[0] = 0;
            log[1] = 0;
            log[2] = System.currentTimeMillis();
            try {
                createAClog();
            } catch (FileNotFoundException ex1) {
                Logger.getLogger(AirConditionTask.class.getName()).log(Level.SEVERE, null, ex1);
            } catch (UnsupportedEncodingException ex1) {
                Logger.getLogger(AirConditionTask.class.getName()).log(Level.SEVERE, null, ex1);
            }
           
        }
       return log; 
    }
     private void check_state() throws FileNotFoundException, UnsupportedEncodingException, IOException{
    
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
        else if(roomTemp<(alarm-1)){
                   
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
    
    /**
     * Reads variable setting for Air Conditioning task.
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private void readSettings() throws FileNotFoundException, IOException{
        String path = "/home/pi/NetBeansProjects/RPI_Air_Conditioning/vars.txt";
        File file = new File(path);
       
        if(!file.exists()){
            file.createNewFile();
            saveSettings();
        }
        
        ReadTextFile rf = new ReadTextFile(path);
        String[] lines=rf.openFile();
        
        String text = null;
        String[] parts = lines[0].split(";",4);
        
        if(parts.length==4){
                sim_flag=Boolean.parseBoolean(parts[0]);
                sim_temp=Double.parseDouble(parts[1]);
                alarm=Double.parseDouble(parts[2]);
                schedule_timer=Integer.parseInt(parts[3]);
            }
      
    }
    
     /**
     * Saves variable settings to file
     * @return
     * @throws IOException 
     */
    private void saveSettings() throws IOException{
        
        String path = "/home/pi/NetBeansProjects/RPI_Air_Conditioning/vars.txt";
        
        WriteTextFile write = new WriteTextFile(path,false);        
        String data="";
        data=sim_flag+";"+sim_temp+";"+alarm+";"+schedule_timer+"\n";
        write.writeToFile(data);
    }
     
}


