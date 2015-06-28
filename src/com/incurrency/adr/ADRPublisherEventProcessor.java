/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.adr;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPOnDemandQueryResult;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.incurrency.framework.TradingUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
/**
 *
 * @author Admin
 */
public class ADRPublisherEventProcessor implements ActionListener {

    private EPServiceProvider esperEngine;
    private static final Logger logger = Logger.getLogger(ADRPublisherEventProcessor.class.getName());
    public EPStatement ADRStatement=null;
    public com.incurrency.adr.ADRPublisher adrStrategy;
    public com.incurrency.adr.ADRPublisherListener adrListener;
    public com.incurrency.adr.ADRPublisherTickListener tickListener;
    
    public  ADRPublisherEventProcessor(ADRPublisher adr)
    {
        this.adrStrategy=adr;
        adrListener=new com.incurrency.adr.ADRPublisherListener(adrStrategy);
        tickListener=new com.incurrency.adr.ADRPublisherTickListener(adrStrategy);
        
        // Register event class alias for simplicity
        Configuration config = new Configuration();
        config.getEngineDefaults().getThreading().setListenerDispatchPreserveOrder(false);
        config.getEngineDefaults().getThreading().setInsertIntoDispatchPreserveOrder(false); 
//        config.addEventType("TickPrice", "com.adrStrategy.ADRPublisherTickPriceEvent");
       config.addEventType("TickPrice", com.incurrency.adr.ADRPublisherTickPriceEvent.class);
       config.addEventType("ADRPrice", com.incurrency.adr.ADRPublisherEvent.class);

        // Get an engine instance
        esperEngine=EPServiceProviderManager.getProvider("ADR", config);
   //     String test=ADRPublisherTickPriceEvent.class.getName();
  //      esperEngine.getEPAdministrator().getConfiguration().addEventType("TickPrice", ADRPublisherTickPriceEvent.class);
        //Make sure the following jars are in classpath. 1.CGILIB, 2.ANTLR
        esperEngine.initialize();
        System.out.println("Esper engine= " + esperEngine.getURI());

        EPStatement statement;
        String stmt;

        //stmt = "select a.terminal.id as terminal from pattern [ every a=Checkin -> " +
        //        "      ( OutOfOrder(terminal.id=a.terminal.id) and not (Cancelled(terminal.id=a.terminal.id) or Completed(terminal.id=a.terminal.id)) )]";
       
        stmt = "create variable int LASTPRICE  = " + com.ib.client.TickType.LAST;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int CLOSEPRICE  = " + com.ib.client.TickType.CLOSE;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int LASTSIZE  = " + com.ib.client.TickType.LAST_SIZE;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int VOLUME = " + com.ib.client.TickType.VOLUME;
        esperEngine.getEPAdministrator().createEPL(stmt);

        // Create a named window to get the last and close price for a ticker
        stmt = "create window PriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, lastPrice double, closePrice double, lastSize double, volume double)";

        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into PriceWin  ";
        stmt += "select  a.tickerID as tickerID, a.price as lastPrice, b.price as closePrice, c.price as lastSize, d.price as volume ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:unique(tickerID) as a, ";
        stmt += "TickPrice(field = CLOSEPRICE, price > 0).std:unique(tickerID) as b, ";
        stmt += "TickPrice(field = LASTSIZE, price > 0).std:unique(tickerID) as c, ";
        stmt += "TickPrice(field = VOLUME, price > 0).std:unique(tickerID) as d ";
        stmt += "where a.tickerID = b.tickerID ";
        stmt += "and b.tickerID = c.tickerID ";
        stmt += "and c.tickerID = d.tickerID";


        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        // Create the statement to calculate adrStrategy as count(+symbs)/count(-symbs) since prv day close
        stmt = "select count(*, lastPrice > closePrice) as pTicks, ";
        stmt += "sum(volume, lastPrice > closePrice) as pVolume, ";
        stmt += "count(*, lastPrice < closePrice) as nTicks, ";
        stmt += "sum(volume, lastPrice < closePrice) as nVolume, ";
        stmt += "count(*) as tTicks, ";
        stmt += "sum(volume) as volume from PriceWin";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(adrListener);

        // Create a named window to get the last and previous last for each ticker
        stmt = "create window LastPriceWin.std:unique(tickerID) as ";
        stmt += "(tickerID int, price double, lPrice double, lastSize double, lLastSize double)";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        stmt = "insert into LastPriceWin  ";
        stmt += "select a.tickerID as tickerID, last(a.price,0) as price, last(a.price,1) as lPrice, last(b.price,0) as lastSize, last(b.price,1) as lLastSize ";
        stmt += "from TickPrice(field = LASTPRICE, price > 0).std:groupwin(tickerID).win:length(2) as a, ";
        stmt += "TickPrice(field = LASTSIZE, price > 0).std:groupwin(tickerID).win:length(2) as b ";
        stmt += " where a.tickerID = b.tickerID ";
        stmt += "group by a.tickerID, b.tickerID "; 
        statement = esperEngine.getEPAdministrator().createEPL(stmt);

        // Create the statement to calculate tick as count(+symbs)/count(-symbs) since last price for each ticker
        stmt = "select count(*, price > lPrice) as pTicks,sum(lastSize, price > lPrice) as pLastSize, count(*, price < lPrice) as nTicks,sum(lastSize, price < lPrice) as nLastSize, ";
        stmt += "count(*,price=lPrice) as uTicks, sum(lastSize,price=lPrice) as uLastSize, count(*) as tTicks, sum(lastSize) as tLastSize from LastPriceWin ";
        statement = esperEngine.getEPAdministrator().createEPL(stmt);
        statement.addListener(tickListener);
        
        
        //Once ADRPublisher volume, count data is available, we move to ADRPublisher processing
        stmt = "create variable int adr  = " + com.incurrency.adr.ADRPublisherTickType.D_ADR;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int adrTRIN= " + com.incurrency.adr.ADRPublisherTickType.D_TRIN;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int tick  = " + com.incurrency.adr.ADRPublisherTickType.T_TICK;
        esperEngine.getEPAdministrator().createEPL(stmt);
        stmt = "create variable int tickTRIN = " + com.incurrency.adr.ADRPublisherTickType.T_TRIN;
        esperEngine.getEPAdministrator().createEPL(stmt);
        
        
        stmt = "select field,max(price) as high ,min(price) as low, avg(price) as average "
                + "from ADRPrice.win:time("
                +adrStrategy.window
                +" minutes) "
                + "group by field";
        
        ADRStatement = esperEngine.getEPAdministrator().createEPL(stmt);
        //statement.addListener(TurtleMainUI.algo.getParamADR());

        
        
        
        //create debug window/
/*
        if(Launch.input.containsKey("debugscreen")){
        JFrame myFrame = new JFrame("Debug Window");
        myFrame.setLayout( new FlowLayout() );
        myFrame.setSize(300,400);
        myFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
         JButton debugButton = new JButton("Debug");
        myFrame.add(debugButton);
        myFrame.setAlwaysOnTop (true);
         myFrame.setVisible(true);
        debugButton.setVisible(true);
        debugButton.addActionListener(this);
        }
    */
    }
  
  
    @Override
    	public void actionPerformed(ActionEvent e) {
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from LastPriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        TradingUtil.writeToFile("Tick.csv", "tickerID,Price,lPrice,lastSize,lLastSize\n");
        for (EventBean row : result.getArray()) {
            String tickerID=row.get("tickerID")!=null?row.get("tickerID").toString():"";
            String price=row.get("price")!=null?row.get("price").toString():"";
            String lPrice=row.get("lPrice")!=null?row.get("lPrice").toString():"";
            String lastSize=row.get("lastSize")!=null?row.get("lastSize").toString():"";
            String lLastSize=row.get("lLastSize")!=null?row.get("lLastSize").toString():"";
            TradingUtil.writeToFile("Tick.csv", tickerID+","+price+","+lPrice+","+lastSize+","+lLastSize+"\n");
        }
        
        //write ADRPublisher
            query="select * from PriceWin";
            EPOnDemandQueryResult result2 = epRuntime.executeQuery(query);
             TradingUtil.writeToFile("ADR.csv", "tickerID,lastPrice,closePrice,lastSize,volume\n");
             for (EventBean ADRrow : result2.getArray()) {
            String tickerID=ADRrow.get("tickerID")!=null?ADRrow.get("tickerID").toString():"";
            String lastPrice=ADRrow.get("lastPrice")!=null?ADRrow.get("lastPrice").toString():"";
            String closePrice=ADRrow.get("closePrice")!=null?ADRrow.get("closePrice").toString():"";
            String lastSize=ADRrow.get("lastSize")!=null?ADRrow.get("lastSize").toString():"";
            String volume=ADRrow.get("volume")!=null?ADRrow.get("volume").toString():"";
            TradingUtil.writeToFile("ADR.csv", tickerID+","+lastPrice+","+closePrice+","+lastSize+","+volume+"\n");
                    
             }
       }
        
      
        public void debugFireTickQuery(){
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from LastPriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        result = epRuntime.executeQuery(query);
        TradingUtil.writeToFile("Tick.csv", "tickerID,Price,lPrice,lastSize,lLastSize\n");
        for (EventBean row : result.getArray()) {
            TradingUtil.writeToFile("Tick.csv", row.get("tickerID").toString()+","+row.get("price").toString()+","+
                    row.get("lPrice").toString()+","+row.get("lastSize").toString()+","+row.get("lLastSize").toString()+"\n");
            //write ADRPublisher
            query="select * from LastPrice";
            result = epRuntime.executeQuery(query);
             TradingUtil.writeToFile("ADR.csv", "tickerID,lastPrice,closePrice,lastSize,volume\n");
             for (EventBean ADRrow : result.getArray()) {
                  TradingUtil.writeToFile("Tick.csv", ADRrow.get("tickerID").toString()+","+ADRrow.get("lastPrice").toString()+","+
                    ADRrow.get("closePrice").toString()+","+ADRrow.get("lastSize").toString()+","+ADRrow.get("volume").toString()+"\n");

             }
            /*            
            System.out.println("tickerID=" + row.get("tickerID"));
            System.out.println("Price=" + row.get("price"));
            System.out.println("lPrice=" + row.get("lPrice"));
            System.out.println("lastSize=" + row.get("lastSize"));
            System.out.println("lLastSize=" + row.get("lLastSize"));*/
	}
        }
        
        public void debugFireADRQuery(){
        EPRuntime epRuntime = esperEngine.getEPRuntime();        
        System.out.println("PriceWin aggregation output");
        String query="select * from PriceWin";
        EPOnDemandQueryResult result = epRuntime.executeQuery(query);
        result = epRuntime.executeQuery(query);
        for (EventBean row : result.getArray()) {
            System.out.println("tickerID=" + row.get("tickerID"));
            //System.out.println("field=" + row.get("field"));
            System.out.println("lastPrice=" + row.get("lastPrice"));
            System.out.println("closePrice=" + row.get("closePrice"));
            System.out.println("lastSize=" + row.get("lastSize"));
            System.out.println("Volume=" + row.get("volume"));
	}
        }
    public void sendEvent(Object event) {
        esperEngine.getEPRuntime().sendEvent(event);
    }

    public void destroy() {
        esperEngine.destroy();
    }

    public void stopAllStmt()
    {
        esperEngine.getEPAdministrator().stopAllStatements();
    }
    public void startAllStmt()
    {
        esperEngine.getEPAdministrator().startAllStatements();
    }
}
