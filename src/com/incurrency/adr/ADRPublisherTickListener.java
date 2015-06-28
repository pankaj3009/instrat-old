/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.adr;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import com.incurrency.framework.TradingUtil;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.logging.Logger;
//import com.incur.client.strat.MarketApp;

/**
 *
 * @author Admin
 */
public class ADRPublisherTickListener implements UpdateListener{

    private static final Logger logger = Logger.getLogger(ADRPublisherTickListener.class.getName());
    private ADRPublisher adrStrategy;
    
    public ADRPublisherTickListener(ADRPublisher adr){
        this.adrStrategy=adr;
    }
    
    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long tTicks = (Long) newEvents[0].get("tTicks");
        Long pTicks =  (Long) newEvents[0].get("pTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long uTicks =   (Long) newEvents[0].get("uTicks");
        long pVolume= newEvents[0].get("pLastSize")==null? 0:Math.round((double)newEvents[0].get("pLastSize"));
        long nVolume= newEvents[0].get("nLastSize")==null?0:Math.round((double) newEvents[0].get("nLastSize"));
        long tVolume= newEvents[0].get("tLastSize")==null?0:Math.round((double) newEvents[0].get("tLastSize"));
        long uVolume= newEvents[0].get("uLastSize")==null?0:Math.round((double) newEvents[0].get("uLastSize"));
 
        String message = "Tick: TotalTicks: " + tTicks + " (+)Ticks: " + pTicks + " (-)Ticks: " + nTicks + " Unchanged: " + uTicks;
        message += " (+)Vol: "+pVolume+" (-)Vol:"+nVolume+" Tot LastSize:"+ tVolume;
                long now =TradingUtil.getAlgoDate().getTime();
        ADRPublisher.adrServer.send("IND:CUS:ALL",6+","+now+","+pTicks +","+"ADR");
        ADRPublisher.adrServer.send("IND:CUS:ALL",7+","+now+","+nTicks +","+"ADR" );
        ADRPublisher.adrServer.send("IND:CUS:ALL",8+","+now+","+tTicks +","+"ADR");
        ADRPublisher.adrServer.send("IND:CUS:ALL",9+","+now+","+pVolume +","+"ADR" );
        ADRPublisher.adrServer.send("IND:CUS:ALL",10+","+now+","+nVolume +","+"ADR");
        ADRPublisher.adrServer.send("IND:CUS:ALL",11+","+now+","+tVolume +","+"ADR" );
        ADRPublisher.adrServer.send("IND:CUS:ALL",12+","+now+","+uTicks +","+"ADR");
        ADRPublisher.adrServer.send("IND:CUS:ALL",13+","+now+","+uVolume +","+"ADR");

        double tick=pTicks+nTicks>0?pTicks*100/(pTicks+nTicks):0;
        double tickTRIN=pVolume+nVolume>0?tick*100/(pVolume*100/(pVolume+nVolume)):0;

        if(tTicks>adrStrategy.threshold){
          adrStrategy.tick=tick;
          adrStrategy.tickTRIN=tickTRIN;
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(ADRPublisherTickType.T_TICK,ADRPublisherTickType.T_TICK,tick));
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(ADRPublisherTickType.T_TRIN,ADRPublisherTickType.T_TRIN,tickTRIN));         
          }
    }
}
