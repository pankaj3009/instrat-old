/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import com.incurrency.framework.Parameters;
import com.incurrency.framework.TWSConnection;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.zeromq.ZMQ;

/**
 *
 * @author pankaj
 */
public class ServerResponse implements Runnable {

    ZMQ.Context context = ZMQ.context(1);
    public static ZMQ.Socket responder;
    private AtomicInteger requestid=new AtomicInteger(0);
    private static final Logger logger = Logger.getLogger(ServerPubSub.class.getName());
    private ArrayList<HistoricalDataParameters> hdSymbols = new ArrayList<>();

    public ServerResponse(int port) {
        responder = context.socket(ZMQ.REP);
        System.out.println("port for responseserver:"+port);
        responder.bind("tcp://*:" + port);
        //publisher.bind("ipc://weather");
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            // Wait for next request from the client
            byte[] requestBytes = responder.recv(0);
            //Get request type
            String request = new String(requestBytes, ZMQ.CHARSET);
            String response = "-1";
            String args[]=request.split(":");
            response = request+processRequest(args);
            /*
            if (args.length == 2) {
                String requestType = request.split(":")[0];
                String symbol = request.split(":")[1];
                response = request + ":" + processRequest(args);
            } else if (args.length == 3) {
                String requestType = request.split(":")[0];
                String symbol = request.split(":")[1];
                String parameters = request.split(":")[2];
                response = request + ":" + processRequest(args);
            }else if(args.length == 4){//historical data request request/response mode
                String requestType = request.split(":")[0];
                String symbol = request.split(":")[1];
                String metric=request.split(":")[3];
                String parameters = request.split(":")[2];                
                response = request + processRequest(args);
            }
      */
            responder.send(response.getBytes(ZMQ.CHARSET), 0);
                    

        }
        responder.close();
        context.term();

    }

    private String processRequest(String [] args) {
        
        switch (args[0]) {
            case "contractid":
                String components[] = args[1].split("_");
                int id = Utilities.getIDFromDisplayName(Parameters.symbol,args[1]);
                /*
                if (components.length == 2) {//STK
                    id = TradingUtil.getIDFromSymbol(components[0], components[1], "", "", "");
                } else if (components.length == 3) {//FUT
                    id = TradingUtil.getIDFromSymbol(components[0], components[1], components[2], "", "");
                } else if (components.length == 5) {//OPT
                    id = TradingUtil.getIDFromSymbol(components[0], components[1], components[2], components[3], components[4]);
                }
                */
                if (id >= 0) {
                    return "_"+String.valueOf(Parameters.symbol.get(id).getContractID() + "_" + String.valueOf(Parameters.symbol.get(id).getTickSize()));
                } else {
                    return "_"+"-1";
                }

            case "historicaldata": //multiple symbols using pub sub
                switch (args[1]) {
                    case "finished":
                        new Thread(new HistoricalDataPublisher(hdSymbols)).start();
                        //HistoricalDataPublisher h= new HistoricalDataPublisher(hdSymbols);
                        break;
                    default:
                        String[] symbolArray = args[1].split("_");
                        HistoricalDataParameters hd;//= new HistoricalDataParameters();
                        switch (symbolArray.length) {
                            case 1:
                            case 2:
                            case 3:
                            case 5:
                            case 7:
                                hd = new HistoricalDataParameters(args[1], args[2].split(",")[0], args[2].split(",")[1], args[2].split(",")[2],args[2].split(",")[3],args[3]);
                                break;
                            default:
                                return "-1";
                            
                        }
                        hd.displayName = args[1];
                        hd.topic=args[2].split(",")[4];
                        hdSymbols.add(hd);
                        return(":"+"processed");
                        //break;
                }
                break;
            case "backfill": //one symbol at a time, using request/response
                String requestidReceived=args[4];  
                int reqid=Integer.valueOf(requestidReceived);
                requestid.set(Math.max(requestid.get(), reqid));
                long startDate=Long.parseLong(args[2].split(",")[1]);
                long endDate=Long.parseLong(args[2].split(",")[2]);
                new Thread(new HistoricalDataResponse(requestidReceived,args[1],args[3],startDate,endDate)).run();
                return(":"+"finished");
                
            case "requestid":
                //return argument+":"+metric+":"+parameters+","+String.valueOf(requestid.addAndGet(1));
              return ":"+String.valueOf(requestid.addAndGet(1));
            case "snapshot":
                components = args[1].split("_");
                 id = Utilities.getIDFromDisplayName(Parameters.symbol,args[1]);
                 if (id >= 0) {
                    return "_"+TWSConnection.marketData[id][com.ib.client.TickType.OPEN] + "_"+TWSConnection.marketData[id][com.ib.client.TickType.HIGH]+"_"+TWSConnection.marketData[id][com.ib.client.TickType.LOW]+"_"+TWSConnection.marketData[id][com.ib.client.TickType.CLOSE]+"_"+TWSConnection.marketData[id][com.ib.client.TickType.LAST]+"_"+TWSConnection.marketData[id][com.ib.client.TickType.VOLUME]; 
                } else {
                    return "_"+"-1";
                }
        }
        return "";
    }
}
