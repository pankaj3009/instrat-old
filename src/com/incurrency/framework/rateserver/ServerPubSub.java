/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import com.incurrency.framework.TWSConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.zeromq.ZMQ;

/**
 *
 * @author pankaj
 */
public class ServerPubSub {

    Thread t;
    ZMQ.Context context = ZMQ.context(1);
    ZMQ.Socket publisher;
    private static final Logger logger = Logger.getLogger(ServerPubSub.class.getName());
    public TickEventSupport tickEventSupport = new TickEventSupport();
    
    public ServerPubSub(int port) {
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
        //logger.log(Level.INFO,"Method:{0}, Message:{1}:{2}", new Object[]{Thread.currentThread().getStackTrace()[1].getMethodName(), topic,message});
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
