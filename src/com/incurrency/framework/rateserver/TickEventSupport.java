/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import java.util.ArrayList;

/**
 *
 * @author pankaj
 */
public class TickEventSupport implements Runnable{
    
    private ArrayList<TickListener> listener=new ArrayList();
    Thread t;
    
    public TickEventSupport(){
        t = new Thread(this, "Tick Event Support");
        t.start();

    }

    public  void addTickListener(TickListener l){
        listener.add(l);
    }
    
    public  void removeTickListener(TickListener l){
        listener.remove(l);
    }
    
    public  void fireTickEvent(String topic, String message){
        TickEvent tickEvent=new TickEvent(new Object(),topic,message);
        for(TickListener l:listener){
            l.tickReceived(tickEvent);
        }
    }

    @Override
    public void run() {
        while(true){
            
        }

    }
    
    
}
