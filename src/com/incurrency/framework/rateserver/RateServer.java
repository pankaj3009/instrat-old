/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;
import java.util.logging.Logger;
import org.zeromq.ZMQ;
/**
 *
 * @author pankaj
 */
public class RateServer {
     ZMQ.Context context = ZMQ.context(1);
     ZMQ.Socket publisher;
     private static final Logger logger = Logger.getLogger(RateServer.class.getName());
     
     public RateServer(int port){
        publisher = context.socket(ZMQ.PUB);
        publisher.bind("tcp://*:"+port);
        //publisher.bind("ipc://weather");
     }
     
     public synchronized void send(String topic, String message){
         publisher.sendMore(topic);
         publisher.send(message,0);
         //logger.log(Level.INFO,"Method:{0}, Message:{1}:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), topic,message});
     }
     
     public synchronized void close(){
         publisher.close ();
        context.term ();
     }
     
}
