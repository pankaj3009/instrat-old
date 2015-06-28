/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * This Class will populate data into a table. 
 * Other parts of the program will read this table.
 * The intention is to seggregate data acquisition from the algorithm logic
 */
package com.incurrency.framework.fundamental;

import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Parameters;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @return the fundamentalRatios
 */
/**
 * @param fundamentalRatios the fundamentalRatios to set
 */
/**
 *
 * @author admin
 */
public class FundamentalData implements Runnable, FundamentalDataListener {

    private Map<String, Fundamental> fundamentalData = new HashMap<>();
    private Map<Integer, String> reqIDToFinancialRatio = new HashMap<>();
    private Map<Integer, String> reqIDToFinancialSnapshot = new HashMap<>();
    public static boolean fundamentalDataReceived = false;
    //private ArrayList<FundamentalRatio> fundamentalRatios=new ArrayList();
    //private Algorithm algo;
    public static long fundamentalMilliSeconds;
    private static final Logger logger=Logger.getLogger(FundamentalData.class.getName());

    public FundamentalData() {
       for (BeanConnection c: Parameters.connection){
           c.getWrapper().addFundamentalListener(this);
       }
    }

    @Override
    public void run() {

        reqFinancialRatios();

    }

    private void reqFinancialRatios() {
         System.out.println("Thread:"+Thread.currentThread().getName());
           int connectionCount=Parameters.connection.size();
           int i=0;
           for (BeanSymbol s : Parameters.symbol) {
                System.out.println("Requesting Fundamental Data. Symbol: "+s.getSymbol());
                Parameters.connection.get(i).getWrapper().requestFundamentalData(s, "snapshot");
                if(s.getFundamental().takeSummary()){
                Parameters.connection.get(i).getWrapper().cancelFundamentalData(s.getFundamental().getSnapshotRequestID());}
                i=i+1;
                if(i>=connectionCount){
                i=0; //reset counter
                try {
                Thread.sleep(11000);
                } catch (InterruptedException ex) {
                  logger.log(Level.SEVERE, null, ex);
                }
                }
         
           
               }
           }
 
 
    /**
     * @return the reqIDToFundamentalData
     */
    public Map<Integer, String> getReqIDToFinancialRatio() {
        return reqIDToFinancialRatio;
    }

    /**
     * @param reqIDToFundamentalData the reqIDToFundamentalData to set
     */
    public void setReqIDToFinancialRatio(Map<Integer, String> reqIDToFundamentalData) {
        this.reqIDToFinancialRatio = reqIDToFundamentalData;
    }

    /**
     * @return the fundamentalData
     */
    public Map<String, Fundamental> getFundamentalData() {
        return fundamentalData;
    }

    /**
     * @param fundamentalData the fundamentalData to set
     */
    public void setFundamentalData(Map<String, Fundamental> fundamentalData) {
        this.fundamentalData = fundamentalData;
    }

   /**
     * @return the reqIDToFinancialSnapshot
     */
    public Map<Integer, String> getReqIDToFinancialSnapshot() {
        return reqIDToFinancialSnapshot;
    }

    /**
     * @param reqIDToFinancialSnapshot the reqIDToFinancialSnapshot to set
     */
    public void setReqIDToFinancialSnapshot(Map<Integer, String> reqIDToFinancialSnapshot) {
        this.reqIDToFinancialSnapshot = reqIDToFinancialSnapshot;
    }

    @Override
    public void fundamentalDataStatus(FundamentalDataEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
