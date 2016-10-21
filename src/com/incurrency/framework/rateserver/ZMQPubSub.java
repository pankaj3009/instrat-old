/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import java.io.PrintStream;
import java.net.Socket;
import java.util.logging.Logger;
import org.zeromq.ZMQ;

/**
 *
 * @author pankaj
 */
public class ZMQPubSub {

    Thread t;
    ZMQ.Context context = ZMQ.context(1);
    ZMQ.Socket publisher;
    private static final Logger logger = Logger.getLogger(ZMQPubSub.class.getName());
    public TickEventSupport tickEventSupport = new TickEventSupport();
    public static PrintStream output;
    public static Socket cassandraConnection;
    public static boolean saveToCassandra=false;
    public static String equityMetric;
    public static String futureMetric;
    public static String optionMetric;
    
    public ZMQPubSub(int port) {
        publisher = context.socket(ZMQ.PUB);
        //publisher.setHWM(100);
        //publisher.setSndHWM(100);
        System.out.println("publisher port:"+port);
        publisher.bind("tcp://*:" + port);
        //publisher.bind("ipc://weather");
        //publisher.setSndHWM(100000);
//        t = new Thread(this, "ServerPubSub");
 //       t.start();

    }
    
    public synchronized void send(String topic, String message) {
        publisher.sendMore(topic);
        publisher.send(message, 0);
   //     logger.log(Level.INFO,"SaveToCassandra:{0}",new Object[]{saveToCassandra});
        if (saveToCassandra) {
            String[] components = message.split(",");
            String tickType = null;
            String metric=null;
            switch (Integer.valueOf(components[0])) {
                case com.ib.client.TickType.ASK:
                    tickType = "ask";
                    break;
                case com.ib.client.TickType.BID:
                    tickType = "bid";
                    break;
                case com.ib.client.TickType.LAST:
                    tickType = "close";
                    break;
                case com.ib.client.TickType.LAST_SIZE:
                    tickType = "volume";
                    break;
                case com.ib.client.TickType.VOLUME:
                    tickType = "dayvolume";
                    break;
                default:
                    break;
            }
               // logger.log(Level.INFO,"components[0]={0},ticktype={1},message={2},componentlength={3}",new Object[]{components[0],tickType,message,components.length});
             if (tickType != null && components.length == 4) {
                String[] symbol = components[3].split("_", -1);
                //logger.log(Level.INFO,"symbol length={0}",new Object[]{symbol.length});
                if(symbol.length==5){
                String expiry = symbol[2];
                String right= symbol[3];
                String strike= symbol[4];
                //logger.log(Level.INFO,"smybol[1]={0}",new Object[]{symbol[1]});
               
                if(symbol[1]!=null){
                    switch(symbol[1]){
                        case "STK":
                        case "IND":
                            metric=equityMetric;
                            break;
                        case "FUT":
                            metric=futureMetric;
                            break;
                        case "OPT":
                            metric=optionMetric;
                            break;
                        default:
                            break;
                    }
                }
                //logger.log(Level.INFO,"SaveToCassandra={0},output={1},ticktype={2},message={3},componentlength={4},symbollength={5}",new Object[]{saveToCassandra,output,tickType,message,components.length,symbol.length});
                if (output != null && metric!=null) {                    
                    new Cassandra(components[2], Long.valueOf(components[1]), metric + "." + tickType, components[3], expiry, right,strike,output).write();
                }
                }
            }
            //logger.log(Level.INFO,"Method:{0}, Message:{1}:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), topic,message});
        }
                        
    }
    
    public synchronized void close() {
        publisher.close();
        context.term();
    }
    /*
    @Override
    public void run() {
        while (true) {
            try {
                if (TWSConnection.getPrices().size() > 0) {
                    Price p = TWSConnection.getPrices().get(0);
                    send(p.getHeader(), p.getMessage());
                    TWSConnection.getPrices().remove(p);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }
*/
}