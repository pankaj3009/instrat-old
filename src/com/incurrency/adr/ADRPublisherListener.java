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

/*
 *
 * @author Admin
 */
public class ADRPublisherListener implements UpdateListener{

    private static final Logger logger = Logger.getLogger(ADRPublisherListener.class.getName());
    private ADRPublisher adrStrategy;
    
    public ADRPublisherListener(com.incurrency.adr.ADRPublisher adr){
        this.adrStrategy=adr;
    }
    
    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {

        DecimalFormat df = new DecimalFormat("0.00");
        df.setMaximumFractionDigits(2);
        df.setMinimumIntegerDigits(1);

        Long pTicks = (Long) newEvents[0].get("pTicks");
        Long tTicks =  (Long) newEvents[0].get("tTicks");
        Long nTicks =  (Long) newEvents[0].get("nTicks");
        Long pVolume = newEvents[0].get("pVolume")==null? 0:Math.round((double)newEvents[0].get("pVolume"));
        Long nVolume = newEvents[0].get("nVolume")==null? 0:Math.round((double)newEvents[0].get("nVolume"));
        Long tVolume = newEvents[0].get("volume")==null? 0:Math.round((double)newEvents[0].get("volume"));
        
        Long uChg = tTicks - (pTicks + nTicks);
        String message = "ADR: TotalMoves: " + tTicks + " (+)Advances: " + pTicks + " (-)Declines: " + nTicks + " Unchanged: " + uChg +" Advancing Volume: "+pVolume +" Declining Volume: "+nVolume+ " Total Volume: "+tVolume;
        long now =TradingUtil.getAlgoDate().getTime();
        ADRPublisher.adrServer.send("IND-CUS-ALL",0+","+now+","+pTicks+","+"ADR");
        ADRPublisher.adrServer.send("IND-CUS-ALL",1+","+now+","+nTicks+","+"ADR" );
        ADRPublisher.adrServer.send("IND-CUS-ALL",2+","+now+","+tTicks+","+"ADR");
        ADRPublisher.adrServer.send("IND-CUS-ALL",3+","+now+","+pVolume+","+"ADR" );
        ADRPublisher.adrServer.send("IND-CUS-ALL",4+","+now+","+nVolume+","+"ADR" );
        ADRPublisher.adrServer.send("IND-CUS-ALL",5+","+now+","+tVolume+","+"ADR" );
        
        double adr=pTicks+nTicks>0?pTicks*100/(pTicks+nTicks):0;
        double adrTRIN=pVolume+nVolume>0?adr*100/(pVolume*100/(pVolume+nVolume)):0;
        if(tTicks>this.adrStrategy.threshold){
          adrStrategy.adr=adr;
          adrStrategy.adrTRIN=adrTRIN;
          adrStrategy.adrDayHigh=adr>adrStrategy.adrDayHigh?adr:adrStrategy.adrDayHigh;
          adrStrategy.adrDayLow=adr<adrStrategy.adrDayLow?adr:adrStrategy.adrDayLow;
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADRPublisherEvent(ADRPublisherTickType.D_ADR,adr));
          adrStrategy.mEsperEvtProcessor.sendEvent(new ADRPublisherEvent(ADRPublisherTickType.D_TRIN,adrTRIN));
        }
    }
    
}
