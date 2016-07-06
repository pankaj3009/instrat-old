/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.fundamental;

import com.incurrency.RatesClient.RequestClient;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumRequestType;
import com.incurrency.framework.Parameters;
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This call is used only for daily bars. For intra-day bars, use
 * HistoricalBarsAll
 *
 * @author pankaj
 */
public class FundamentalData implements Runnable {

    public EnumRequestType[] requestType;
    private static final Logger logger = Logger.getLogger(FundamentalData.class.getName());
    
    public FundamentalData(EnumRequestType[] requestType) {
        this.requestType=requestType;
    }
    

//    public HistoricalBars(String ThreadName){
    //   }
    @Override
    public void run() {
        try {
                    int connectionCount = Parameters.connection.size();
                    int i = 0;
                    for (BeanSymbol s : Parameters.symbol) {
                        if(s.getType().equals("STK")){
                        for(EnumRequestType r:requestType){
                            String targetFileName=s.getDisplayname()+"_"+r.toString()+".xml";
                            File f=new File("logs",targetFileName);
                            if(!f.exists()){
                                //Get next valid connection i
                                while (Parameters.connection.get(i).getHistMessageLimit() == 0) {
                                    i = i + 1;
                                    if (i >= connectionCount) {
                                        i = 0;
                                    }
                                }
                                //Make Fundamental data request using this connection i
                                logger.log(Level.FINE,"Initiating request for Historical Data for:{0}",new Object[]{targetFileName});
                                Parameters.connection.get(i).getWrapper().requestFundamentalData(s, r.toString());
                                i=i+1;
                                if(i>=connectionCount){
                                    i=0;
                                }
                                //Thread.sleep(Parameters.connection.get(0).getHistMessageLimit() * 1000);
                            }
                        }
                        }
                    }
        }catch (Exception e){
         logger.log(Level.INFO,null,e);   
        }
    }
                        
}
