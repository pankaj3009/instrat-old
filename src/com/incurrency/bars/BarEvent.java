/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.bars;

import com.incurrency.framework.*;
import java.util.EventObject;

/**
 *
 * @author admin
 */
public class BarEvent extends EventObject{
    private int _symbolID;
    private long _time;
    private EnumBarSize _barSize;
            
    public BarEvent( Object source, int id,long time, EnumBarSize barSize){
        super(source);
        _symbolID=id;
        _time=time;
        _barSize=barSize;
    }

    /**
     * @return the _symbolID
     */
    public int getSymbolID() {
        return _symbolID;
    }

    /**
     * @param symbolID the _symbolID to set
     */
    public void setSymbolID(int symbolID) {
        this._symbolID = symbolID;
    }

    /**
     * @return the _barSize
     */
    public EnumBarSize getBarSize() {
        return _barSize;
    }

    /**
     * @param barSize the _barSize to set
     */
    public void setBarSize(EnumBarSize barSize) {
        this._barSize = barSize;
    }

    /**
     * @return the _time
     */
    public long getTime() {
        return _time;
    }

    /**
     * @param time the _time to set
     */
    public void setTime(long time) {
        this._time = time;
    }




}
