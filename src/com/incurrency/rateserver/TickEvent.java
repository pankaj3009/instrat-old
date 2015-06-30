/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.rateserver;

import java.util.EventObject;

/**
 *
 * @author pankaj
 */
public class TickEvent extends EventObject {
    
    private String _topic;
    private String _message;
    
    public TickEvent(Object o,String topic, String message){
        super(o);
        this._topic=topic;
        this._message=message;
    }

    /**
     * @return the _topic
     */
    public String getTopic() {
        return _topic;
    }

    /**
     * @param topic the _topic to set
     */
    public void setTopic(String topic) {
        this._topic = topic;
    }

    /**
     * @return the _message
     */
    public String getMessage() {
        return _message;
    }

    /**
     * @param message the _message to set
     */
    public void setMessage(String message) {
        this._message = message;
    }
}
