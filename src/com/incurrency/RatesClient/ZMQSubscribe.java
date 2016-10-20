/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.TradingEventSupport;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.zeromq.ZMQ;
import static com.incurrency.framework.Algorithm.*;
/**
 *
 * @author pankaj
 */
public class ZMQSubscribe {

    private ZMQ.Context context = ZMQ.context(1);
    private static final Logger logger = Logger.getLogger(ZMQSubscribe.class.getName());
    private ZMQ.Socket subscriber;
    private ArrayList _listeners = new ArrayList();
    public static TradingEventSupport tes ;
    //BlockingQueue <String>queue = new ArrayBlockingQueue<>(100000);
    //ExecutorService pool=Executors.newFixedThreadPool(Integer.parseInt(MainAlgorithm.input.get("threads")));
    //ExecutorService pool=Executors.newCachedThreadPool();
    //ExecutorService pool=Executors.newFixedThreadPool(2000);
    ExecutorService pool;
    private boolean contextOpen=false;
    
    
    public ZMQSubscribe(String path, String topic) {
        subscriber = context.socket(ZMQ.SUB);
        setContextOpen(true);
        subscriber.connect("tcp://" + path);
        tes=new TradingEventSupport();
        subscriber.subscribe(topic.getBytes());
        if(globalProperties.getProperty("threadlimit")!=null){
            int limit=Integer.valueOf(globalProperties.getProperty("threadlimit").toString().trim());
            pool=Executors.newFixedThreadPool(limit);
        }else{
            pool=Executors.newCachedThreadPool();
        }


    }

    public void receive(String topic) {
        int i=0;
        while (isContextOpen()) {
            String string = getSubscriber().recvStr(0).trim();
            if(string!=null){
                try {
                    //queue.put(string);
                    if(string.substring(0,1).matches("\\d")){
                    //logger.log(Level.INFO,"Input String:{0}",new Object[]{string});
                    pool.execute(new Task(string));
                    }
                } catch (Exception ex) {
                   logger.log(Level.SEVERE, null, ex);
                   pool.shutdown();
                }
            }
        }

    }

    public void close() {
       pool.shutdown();
        setContextOpen(false);
        getSubscriber().close();
        getContext().term();
    }

    /**
     * @return the subscriber
     */
    public ZMQ.Socket getSubscriber() {
        return subscriber;
    }

    /**
     * @param subscriber the subscriber to set
     */
    public  void setSubscriber(ZMQ.Socket subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * @return the context
     */
    public ZMQ.Context getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(ZMQ.Context context) {
        this.context = context;
    }

    /**
     * @return the contextOpen
     */
    public boolean isContextOpen() {
        return contextOpen;
    }

    /**
     * @param contextOpen the contextOpen to set
     */
    public void setContextOpen(boolean contextOpen) {
        this.contextOpen = contextOpen;
    }
}
