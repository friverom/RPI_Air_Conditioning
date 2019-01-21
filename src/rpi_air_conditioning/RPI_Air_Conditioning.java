/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_air_conditioning;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Federico
 */
public class RPI_Air_Conditioning {

    static AirConditionTask ac_task = null;
    static ServerSocket serversocket = null;
    static Socket socket = null;
    static InputStream in = null;
    static BufferedReader input = null;
    static PrintWriter output = null;
    static boolean runFlag = true;

    public static void main(String[] args) throws IOException {
        //args[0] = IP address of remote rpi module.
        // if null, rpi module is localhost
        if (args.length == 0) {
            ac_task = new AirConditionTask();
        } else {
            ac_task = new AirConditionTask(args[0]);
        }
        ac_task.start();

        //Start to listen on port 30004 for commands
        try {
            serversocket = new ServerSocket(30004);
        } catch (IOException ex) {
            Logger.getLogger(RPI_Air_Conditioning.class.getName()).log(Level.SEVERE, null, ex);
        }
        // Loop until Kill Thread command received
        while (runFlag) {
            try {
                waitRequest(); //Wait for command and process request.
            } catch (IOException ex) {
                Logger.getLogger(RPI_Air_Conditioning.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        serversocket.close();
    
    }
    
    /**
     * This method wait for a connection, get the command.
     * @throws IOException 
     */
    private static void waitRequest() throws IOException {
       
        String request = "";
        String reply = "";
        socket = serversocket.accept();
        in = socket.getInputStream();
        input = new BufferedReader(new InputStreamReader(in));
        output = new PrintWriter(socket.getOutputStream(), true);
        request = input.readLine(); //Get Command
        reply = processRequest(request); //Process command
        output.println(reply);
        input.close();
        output.close();
    }
    
    /**
     * This method Process the command
     * @param request
     * @return 
     */
    private static String processRequest(String request) throws IOException{
        String reply="";
        String command="";
        double data=0;
        
        String parts[]=request.split(",");
        
        if(parts.length==1){
            command=request;
        }else{
            command=parts[0];
            data=Double.parseDouble(parts[1]);
        }
        
        switch(command){
            case "get status":
                reply=ac_task.getStatus();
                break;
            case "kill thread":
                runFlag = false;
                reply=ac_task.killThread();
                break;
            case "set sim":
                reply=ac_task.setSimFlag();
                break;
            case "reset sim":
                reply=ac_task.resetSimFlag();
                break;
            case "set sim temp":
                reply = ac_task.setSimTemp(data);
                break;
            case "get sim temp":
                reply=ac_task.getSimTemp();
                break;
            case "set alarm temp":
                reply = ac_task.setAlarmTemp(data);
                break;
            case "get alarm temp":
                reply = ac_task.getAlarmTemp();
                break;
            case "reset alarm":
                reply = ac_task.resetAlarm();
                break;
            case "ack alarm":
                reply = ac_task.alarmAck();
                break;
            case "set schedule":
                reply= ac_task.setScheduleTimer((int)data);
                break;
            case "get schedule":
                reply=ac_task.getScheduleTimer();
                break;
            case "get temperature":
                reply = ac_task.getTemperature();
                break;
            case "get temp log":
                ac_task.getTempLog((int)data);
                break;
            default:
                reply="invalid command";
        }
        return reply;
    }
}

