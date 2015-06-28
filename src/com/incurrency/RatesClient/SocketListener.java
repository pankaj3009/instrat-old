/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class SocketListener implements Runnable {
    
    private Subscribe subs;
    private String topic;
    private final static Logger logger = Logger.getLogger(SocketListener.class.getName());



    public SocketListener(String ip, String port, String topic) {
        subs = new Subscribe(ip + ":" + port,topic.toUpperCase());
        this.topic = topic;
    }

    @Override
    public void run() {
        while (subs.isContextOpen()) {
            getSubs().receive(topic);
        }
    }

    /**
     * @return the subs
     */
    public  Subscribe getSubs() {
        return subs;
    }

    /**
     * @param subs the subs to set
     */
    public void setSubs(Subscribe subs) {
        this.subs = subs;
    }
}
