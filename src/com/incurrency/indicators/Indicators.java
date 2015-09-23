/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.indicators;

import com.google.common.base.Preconditions;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.ExtendedHashMap;
import com.incurrency.framework.MatrixMethods;
import com.incurrency.framework.ReservedValues;
import com.incurrency.framework.Utilities;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jblas.DoubleMatrix;
import java.text.SimpleDateFormat;
import static com.incurrency.framework.MatrixMethods.*;
import java.util.Arrays;

/**
 *
 * @author Pankaj
 */
public class Indicators {
    
 private static ExtendedHashMap<String, Object> output = new ExtendedHashMap<>();
 private final Object lockSwing = new Object();
 private static final Object lockStddev = new Object();
 private static final Object lockma = new Object();
 private static final Object lockatr = new Object();
 private static final Object lockrsi = new Object();
 private static final Object lockzscore = new Object();

    
    private static final Logger logger = Logger.getLogger(Indicators.class.getName());

    /**
     * Generates swing for a beansymbol, for specified barsize.Sets "swing", "stickyswing", 
     * "swingh", "swingl", "swingh_1", "swingl_1", "barsinswing", "barsoutsideswing", "updownbar"
     * in beansymbol.
     * @param s
     * @param barSize
     * @return 
     */
    public BeanSymbol swing(BeanSymbol s, EnumBarSize barSize) {
        synchronized(lockSwing){
        try {
            Preconditions.checkArgument(s.getTimeSeries(barSize, "settle").length > 0, "Bar for symbol: %s, Barsize: %s does not have any data", s.getDisplayname(), barSize.toString());
            DoubleMatrix mO = s.getTimeSeries(barSize, "open");
            DoubleMatrix mH = s.getTimeSeries(barSize, "high");
            DoubleMatrix mL = s.getTimeSeries(barSize, "low");
            DoubleMatrix mC = s.getTimeSeries(barSize, "settle");
            //get missing data
            int[] indices = mH.ne(ReservedValues.EMPTY).findIndices();
            mO = mO.get(indices).reshape(1, indices.length);
            mH = mH.get(indices).reshape(1, indices.length);
            mL = mL.get(indices).reshape(1, indices.length);
            mC = mC.get(indices).reshape(1, indices.length);
            List<Long> time=Utilities.subList(indices, BeanSymbol.columnLabels.get(barSize));

            if (mH.length > 0) {
                List<Long> dT = s.getColumnLabels().get(barSize);
                dT = Utilities.subList(indices, dT);
                DoubleMatrix HH = new DoubleMatrix();
                DoubleMatrix HL = new DoubleMatrix();
                DoubleMatrix LH = new DoubleMatrix();
                DoubleMatrix LL = new DoubleMatrix();
                DoubleMatrix EL = new DoubleMatrix();
                DoubleMatrix EH = new DoubleMatrix();
                DoubleMatrix mH_1 = MatrixMethods.ref(mH, -1);
                DoubleMatrix mL_1 = MatrixMethods.ref(mL, -1);
                mH.gti(mH_1, HH);
                mH.lti(mH_1, LH);
                mL.gti(mL_1, HL);
                mL.lti(mL_1, LL);
                mL.eqi(mL_1,EL);
                mH.eqi(mH_1, EH);
                
                DoubleMatrix upbar = HH.and(HL);
                DoubleMatrix downbar = LL.and(LH);
                DoubleMatrix outsidebar = (HH.and(LL)).or(EH.and(LL)).or(HH.and(LL));
                DoubleMatrix upbar_1 = MatrixMethods.ref(upbar, -1);
                DoubleMatrix downbar_1 = MatrixMethods.ref(downbar, -1);

                upbar = upbar.or(upbar_1.and(LH.and(HL))).or(upbar_1.and(HH.and(LL)));
                downbar = downbar.or(downbar_1.and(LH.and(HL))).or(downbar_1.and(HH.and(LL)));
                downbar.negi();
                DoubleMatrix updownbar = upbar.add(downbar);
                updownbar = MatrixMethods.valueWhen(updownbar, updownbar, 1);
                DoubleMatrix updownbarclean=updownbar.mul(outsidebar.not());
//                DoubleMatrix updownbarclean=upbar.add(downbar.mul(-1));
                
                //***** ADJUST UPDOWNBAR FOR OUTSIDE BAR
                int[] outsidebarindices = outsidebar.eq(1).findIndices();
                updownbar.put(outsidebarindices, DoubleMatrix.zeros(outsidebarindices.length));
                DoubleMatrix indet_1 = MatrixMethods.ref(updownbar.eq(0), -1);
                DoubleMatrix indet_2 = MatrixMethods.ref(updownbar.eq(0), -2);
                DoubleMatrix indet_3 = MatrixMethods.ref(updownbar.eq(0), -3);
                DoubleMatrix indet_4 = MatrixMethods.ref(updownbar.eq(0), -4);
                DoubleMatrix up_1 = MatrixMethods.ref(updownbar.eq(1), -1);
                DoubleMatrix up_2 = MatrixMethods.ref(updownbar.eq(1), -2);
                DoubleMatrix up_3 = MatrixMethods.ref(updownbar.eq(1), -3);
                DoubleMatrix up_4 = MatrixMethods.ref(updownbar.eq(1), -4);
                DoubleMatrix up_5 = MatrixMethods.ref(updownbar.eq(1), -5);
                DoubleMatrix down_1 = MatrixMethods.ref(updownbar.eq(-1), -1);
                DoubleMatrix down_2 = MatrixMethods.ref(updownbar.eq(-1), -2);
                DoubleMatrix down_3 = MatrixMethods.ref(updownbar.eq(-1), -3);
                DoubleMatrix down_4 = MatrixMethods.ref(updownbar.eq(-1), -4);
                DoubleMatrix down_5 = MatrixMethods.ref(updownbar.eq(-1), -5);
                DoubleMatrix up = up_1.or(indet_1.and(up_2)).or(indet_1.and(indet_2).and(up_3)).or(indet_1.and(indet_2).and(indet_3).and(up_4)).or(indet_1.and(indet_2).and(indet_3).and(indet_4).and(up_5));
                DoubleMatrix down = down_1.or(indet_1.and(down_2)).or(indet_1.and(indet_2).and(down_3)).or(indet_1.and(indet_2).and(indet_3).and(down_4)).or(indet_1.and(indet_2).and(indet_3).and(indet_4).and(down_5));

                DoubleMatrix updownbaradj = updownbar.eq(0).mul(down.sub(up));
                updownbar = updownbar.add(updownbaradj);
                //***** COMPLETE - ADJUST UPDOWN BAR FOR OUTSIDE BAR
                
                DoubleMatrix swingHigh=fnHighSwing(updownbar,mH);
                DoubleMatrix swingHighSignal = swingHigh.ne(ref( swingHigh, -1 ));
                swingHighSignal = swingHigh.mul(swingHighSignal);
                DoubleMatrix swingHighSignal_1 = fnAlignedShift( swingHighSignal );
                DoubleMatrix swingHighSignal_2 = fnAlignedShift( swingHighSignal_1 );
                
                DoubleMatrix swingLow = fnLowSwing( updownbar, mL );
                DoubleMatrix swingLowSignal = swingLow.ne(ref( swingLow, -1 ));
                swingLowSignal = swingLow.mul(swingLowSignal);
                DoubleMatrix swingLowSignal_1 = fnAlignedShift( swingLowSignal );
                DoubleMatrix swingLowSignal_2 = fnAlignedShift( swingLowSignal_1 );
                DoubleMatrix swingHigh_1 = valueWhen( swingHighSignal_1, swingHighSignal_1, 1 );
                DoubleMatrix swingLow_1 = valueWhen( swingLowSignal_1, swingLowSignal_1, 1 );
                DoubleMatrix swingHigh_2 = valueWhen( swingHighSignal_2, swingHighSignal_2, 1 );
                DoubleMatrix swingLow_2 = valueWhen( swingLowSignal_2, swingLowSignal_2, 1 );
                 
                /*
                SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd");
                
                for(int i=0;i<time.size();i++){
                    long bartime=time.get(i);
                    output.add(sdf.format(new Date(bartime)), "upbar", updownbar.get(i));
                    output.add(sdf.format(new Date(bartime)), "outsidebar", outsidebar.get(i));
                    output.add(sdf.format(new Date(bartime)), "swingh", swingHigh.get(i));
                }
                Utilities.print(output, "dailyscan" + ".csv", new String[]{"upbar","outsidebar","out_1","out_2","out_3","out_4","out_5","out_6","trend"});
*/

                DoubleMatrix out_1 = updownbarclean.eq(1).and((swingHigh.gt(swingHigh_1)).and(swingLow.gt(swingLow_1))); //upbar which has made a higher high
                DoubleMatrix out_2 = updownbarclean.eq(1).and((swingHigh_1.gt(swingHigh_2)).and(swingLow.gt(swingLow_1)));//upbar which has not made a higher high
                DoubleMatrix out_3 = (updownbarclean.eq(-1).or(updownbarclean.eq(0))).and((swingHigh.gt(swingHigh_1)).and(swingLow_1.gt(swingLow_2)).and(mL.gt(swingLow_1)));//downbar which has not made a lower low
                DoubleMatrix uptrend = out_1.or(out_2).or(out_3);
                DoubleMatrix out_4 = updownbarclean.eq(-1).and((swingLow.lt(swingLow_1)).and(swingHigh.lt(swingHigh_1))); //downbar which has made a lower low
                DoubleMatrix out_5 = updownbarclean.eq(-1).and((swingLow_1.lt(swingLow_2)).and(swingHigh.lt(swingHigh_1)));//downbar which has not made a lower low
                DoubleMatrix out_6 = (updownbarclean.eq(1).or(updownbarclean.eq(0))).and((swingLow.lt(swingLow_1)).and(swingHigh_1.lt(swingHigh_2)).and(mH.lt(swingHigh_1)));//upbar which has not made a higher high
                DoubleMatrix downtrend = out_4.or(out_5).or(out_6);
                downtrend.negi();
                DoubleMatrix trend = uptrend.add(downtrend);
                DoubleMatrix stickyTrend = MatrixMethods.valueWhen(trend, trend, 1);
                DoubleMatrix flipTrend=(trend.eq(stickyTrend).mul(stickyTrend)).
                add(trend.ne(stickyTrend).mul(stickyTrend.mul(-1)));
                DoubleMatrix swingLevel = updownbar.eq(1).mul(swingHigh).add(updownbar.ne(1).mul(swingLow));
                DoubleMatrix daysoutsidetrend=barsSince(trend);
                DoubleMatrix daysintrend=barsSince(trend.eq(0));
                DoubleMatrix daysinuptrend=barsSince(trend.le(0));
                DoubleMatrix daysindowntrend=barsSince(trend.ge(0));
                DoubleMatrix daysinupswing=barsSince(updownbar.le(0));
                DoubleMatrix daysindownswing=barsSince(updownbar.ge(0));
                DoubleMatrix greenBar=mC.gt(mO);
                //DoubleMatrix y=updownbar;
                //DoubleMatrix y_1=ref(updownbar,1);
                DoubleMatrix y=ref(updownbar,1).eq(1);
                SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd");
/*
                for(int i=0;i<time.size();i++){
                    long bartime=time.get(i);
                    output.add(sdf.format(new Date(bartime)), "trend", trend.get(i));
                    output.add(sdf.format(new Date(bartime)), "updownbar", updownbar.get(i));
                    output.add(sdf.format(new Date(bartime)), "greenbar", greenBar.get(i));
                    output.add(sdf.format(new Date(bartime)), "daysinupswing", daysinupswing.get(i));
                    output.add(sdf.format(new Date(bartime)), "daysindownswing", daysindownswing.get(i));    
                    output.add(sdf.format(new Date(bartime)), "daysoutsidetrend", daysoutsidetrend.get(i)); 
                    output.add(sdf.format(new Date(bartime)), "daysintrend", daysintrend.get(i));
                    output.add(sdf.format(new Date(bartime)), "stickytrend", stickyTrend.get(i));
                    output.add(sdf.format(new Date(bartime)), "daysinuptrend", daysinuptrend.get(i));
                    output.add(sdf.format(new Date(bartime)), "daysindowntrend", daysindowntrend.get(i));
                    output.add(sdf.format(new Date(bartime)), "y", y.get(i));
                    //output.add(sdf.format(new Date(bartime)), "y_1", y_1.get(i));
                    //output.add(sdf.format(new Date(bartime)), "y_2", y_2.get(i));
                   
                }
                 Utilities.print(output, "dailyscan" + ".csv", new String[]{"trend","updownbar","greenbar","daysinupswing","daysindownswing","daysoutsidetrend",
                     "daysintrend","stickytrend","daysinuptrend","daysindowntrend","y"});
            
                */
                logger.log(Level.FINE, "Symbol:{0},Swings Calculated:{1}", new Object[]{s.getDisplayname(), trend.length});
                s.setTimeSeries(barSize, dT, new String[]{"trend", "updownbar","updownbarclean","greenbar", "daysinupswing", "daysindownswing", 
                    "daysoutsidetrend", "daysintrend", "stickytrend", "fliptrend","daysinuptrend","daysindowntrend","y"},
                        new double[][]{trend.data, updownbar.data,updownbarclean.data, greenBar.data, daysinupswing.data, daysindownswing.data,
                    daysoutsidetrend.data, daysintrend.data, stickyTrend.data, flipTrend.data,daysinuptrend.data,daysindowntrend.data,y.data});
            }            
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

        return s;
        }
    }

    public DoubleMatrix stddev(DoubleMatrix m, int period){
        DoubleMatrix mout = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        Core c = new Core();
        RetCode retCode;
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        double[] out=new double[m.length];
        int[] indices=m.ne(ReservedValues.EMPTY).findIndices();
        DoubleMatrix input1=MatrixMethods.getSubSetVector(m, indices);
        retCode = c.stdDev(0, input1.length-1, input1.data, period,1, begin, length, out);
        double[] out1=Arrays.copyOfRange(out, 0, length.value);
        double[] na=Utilities.range(ReservedValues.EMPTY, 0, begin.value);
        double []out2=com.google.common.primitives.Doubles.concat(na,out1);
        DoubleMatrix mout1=new DoubleMatrix(out2).reshape(1, out2.length);
        mout.put(indices, mout1);
        return mout;
    }
    
    public  DoubleMatrix ma(DoubleMatrix m, int period){
        if(period==1){
            return m;
        }else{
        DoubleMatrix mout = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        Core c = new Core();
        RetCode retCode;
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        double[] out=new double[m.length];
        int[] indices=m.ne(ReservedValues.EMPTY).findIndices();
        DoubleMatrix input1=MatrixMethods.getSubSetVector(m, indices);
        retCode = c.sma(0, input1.length-1, input1.data, period, begin, length, out);
        double[] out1=Arrays.copyOfRange(out, 0, length.value);
        double[] na=Utilities.range(ReservedValues.EMPTY, 0, begin.value);
        double []out2=com.google.common.primitives.Doubles.concat(na,out1);
        DoubleMatrix mout1=new DoubleMatrix(out2).reshape(1, out2.length);
        mout.put(indices, mout1);
        return mout;
        }
    }
    
    public  DoubleMatrix atr(DoubleMatrix high,DoubleMatrix low,DoubleMatrix close, int period){
        DoubleMatrix mout = MatrixMethods.create(ReservedValues.EMPTY, high.length);
        Core c = new Core();
        RetCode retCode;
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        double[] out=new double[high.length];
        int[] indices=high.ne(ReservedValues.EMPTY).findIndices();
        indices=Utilities.addArraysNoDuplicates(indices, low.ne(ReservedValues.EMPTY).findIndices());
        indices=Utilities.addArraysNoDuplicates(indices, close.ne(ReservedValues.EMPTY).findIndices());
        high=MatrixMethods.getSubSetVector(high, indices);
        low=MatrixMethods.getSubSetVector(low, indices);
        close=MatrixMethods.getSubSetVector(close, indices);
        retCode = c.atr(0, high.length-1, high.data,low.data,close.data,period, begin, length, out);
        double[] out1=Arrays.copyOfRange(out, 0, length.value);
        double[] na=Utilities.range(ReservedValues.EMPTY, 0, begin.value);
        double []out2=com.google.common.primitives.Doubles.concat(na,out1);
        DoubleMatrix mout1=new DoubleMatrix(out2).reshape(1, out2.length);
        mout.put(indices, mout1).reshape(1, mout.length);
        return mout;
//        return ma(mout,period);
    }
    
    public  DoubleMatrix rsi(DoubleMatrix m, int period){
        DoubleMatrix mout = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        Core c=new Core();
        RetCode retCode;
        MInteger begin = new MInteger();
        MInteger length = new MInteger();
        double[] out=new double[m.length];
        int[] indices=m.ne(ReservedValues.EMPTY).findIndices();
        DoubleMatrix input1=MatrixMethods.getSubSetVector(m, indices);
        retCode=c.rsi(0, input1.length-1, input1.data, period, begin, length, out);
        double[] out1=Arrays.copyOfRange(out, 0, length.value);
        double[] na=Utilities.range(ReservedValues.EMPTY, 0, begin.value);
        double []out2=com.google.common.primitives.Doubles.concat(na,out1);
        DoubleMatrix mout1=new DoubleMatrix(out2).reshape(1, out2.length);
        mout.put(indices, mout1);
        return mout;
    }
    
    /**
     * Calculates Z Score.Returned size = input size, with gaps filled by NA.
     * @param m
     * @param period
     * @return 
     */
    public  DoubleMatrix zscore(DoubleMatrix m, int period) {
        DoubleMatrix out = MatrixMethods.create(ReservedValues.EMPTY, m.length);
        DoubleMatrix dma = new Indicators().ma(m, period);
        DoubleMatrix dstd = new Indicators().stddev(m, period);
        int[] indices = MatrixMethods.getValidIndices(dma, dstd);
        dma=MatrixMethods.getSubSetVector(dma, indices);
        dstd=MatrixMethods.getSubSetVector(dstd, indices);
        m=MatrixMethods.getSubSetVector(m, indices);
        DoubleMatrix out1 = (m.sub(dma)).div(dstd);
        out.put(indices, out1);
        return out;
    }
    
    
    private  DoubleMatrix fnHighSwing(DoubleMatrix conditionArray,DoubleMatrix priceArray){
        DoubleMatrix lowsignal=MatrixMethods.cross(MatrixMethods.create(0D, conditionArray.length), conditionArray);
        DoubleMatrix highsignal=MatrixMethods.cross(conditionArray, MatrixMethods.create(0D, conditionArray.length));
        DoubleMatrix tValues=MatrixMethods.highestSince(highsignal, priceArray, 1);
        DoubleMatrix tSignalValues=tValues.mul(lowsignal);
        DoubleMatrix ones=MatrixMethods.create(1D, conditionArray.length);
        int length=ones.length;
        DoubleMatrix lastbar=MatrixMethods.cum(ones).eq(MatrixMethods.cum(ones).get(length-1));
        tSignalValues=(lastbar.eq(1).mul(tValues)).add(lastbar.ne(1).mul(tSignalValues));
        DoubleMatrix tSignal=MatrixMethods.valueWhen(tSignalValues,tSignalValues,0);
        DoubleMatrix tSignalPosition = conditionArray.ge(0);
        tSignalPosition = MatrixMethods.exrem( tSignalPosition, MatrixMethods.ref( tSignalPosition, -1 ) );
        tSignal=tSignal.mul(tSignalPosition);
        tSignal=MatrixMethods.valueWhen(tSignal, tSignal, 1);
        return tSignal;
    }

      private  DoubleMatrix fnLowSwing(DoubleMatrix conditionArray,DoubleMatrix priceArray){
        DoubleMatrix lowsignal=MatrixMethods.cross(MatrixMethods.create(0D, conditionArray.length), conditionArray);
        DoubleMatrix highsignal=MatrixMethods.cross(conditionArray, MatrixMethods.create(0D, conditionArray.length));
        DoubleMatrix tValues=MatrixMethods.lowestSince(lowsignal, priceArray, 1);
        DoubleMatrix tSignalValues=tValues.mul(highsignal);
        DoubleMatrix ones=MatrixMethods.create(1D, conditionArray.length);
        int length=ones.length;
        DoubleMatrix lastbar=MatrixMethods.cum(ones).eq(MatrixMethods.cum(ones).get(length-1));
        tSignalValues=(lastbar.eq(1).mul(tValues)).add(lastbar.ne(1).mul(tSignalValues));
        DoubleMatrix tSignal=MatrixMethods.valueWhen(tSignalValues,tSignalValues,0);
        DoubleMatrix tSignalPosition = conditionArray.le(0);
        tSignalPosition = MatrixMethods.exrem( tSignalPosition, MatrixMethods.ref( tSignalPosition, -1 ) );
        tSignal=tSignal.mul(tSignalPosition);
        tSignal=MatrixMethods.valueWhen(tSignal, tSignal, 1);
        return tSignal;
    }

    private  DoubleMatrix fnAlignedShift(DoubleMatrix m){
    DoubleMatrix barsSinceTrigger = MatrixMethods.barsSince( m.gt(0)); //bars since array greater than 0.
    DoubleMatrix dataValues = barsSinceTrigger.eq(0);
    DoubleMatrix shift = MatrixMethods.ref( barsSinceTrigger, -1 ).add(1); //Calculate the shift required by adding 1.
    shift = dataValues.mul(shift) ;//exclude values that do not have a trigger.
    DoubleMatrix negshift=shift.mul(-1);
    return MatrixMethods.ref( m, negshift );
    }  
      
    /**
     * Generates intraday volatility for a symbol.Sets row "intradayvol" in BeanSymbol
     * @param s
     * @return 
     */
    public  BeanSymbol dailyVol(BeanSymbol s) {
        Core c = new Core();
        double intradayVol = 0;
        double dayClose = 0;
        Preconditions.checkArgument(s.getTimeSeriesLength(EnumBarSize.ONESECOND) > 0);
        int timeLength = s.getTimeSeriesLength(EnumBarSize.DAILY);
        long time;
        if (timeLength == -1) { //No value in daily bars. 
            time = s.getColumnLabels().get(EnumBarSize.ONESECOND).get(0);
        } else {
            time = s.getColumnLabels().get(EnumBarSize.DAILY).get(timeLength - 1);
        }
        ArrayList<Long> timeSpan = Utilities.getTimeArray(time, DateUtil.addDays(new Date(time), 1).getTime(), EnumBarSize.ONESECOND, Algorithm.holidays, true, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.timeZone);
        int startIndex = s.getTimeStampIndex(EnumBarSize.ONESECOND, timeSpan.get(0));
        int endIndex = s.getTimeStampIndex(EnumBarSize.ONESECOND, timeSpan.get(timeSpan.size() - 1));
        Preconditions.checkArgument(startIndex >= 0);
        Preconditions.checkArgument(endIndex >= 0);

        DoubleMatrix mClose = s.getTimeSeries(EnumBarSize.ONESECOND, "close").get(Utilities.range(startIndex, 1, endIndex - startIndex + 1));
        mClose = mClose.reshape(1, mClose.rows);
        if (mClose != null) {
            mClose = MatrixMethods.valueWhen(mClose.ne(ReservedValues.EMPTY), mClose, 1);
            double[] close = mClose.data;
            if (close.length > 0) {
                MInteger begin = new MInteger();
                MInteger length = new MInteger();
                double[] out_sma5 = new double[close.length - 1];
                double[] out_sma10 = new double[close.length - 1];
                RetCode retCode;
                retCode = c.sma(0, close.length - 1, close, 5, begin, length, out_sma5);
                retCode = c.sma(0, close.length - 1, close, 10, begin, length, out_sma10);
                dayClose = close[close.length - 1];
                s.setClosePrice(dayClose);
                double dayOpen = close[0];
                s.setOpenPrice(dayOpen);
                double dayHigh = 0;
                double dayLow = Double.MAX_VALUE;
                for (int i = 0; i < close.length - 1; i++) {
                    dayHigh = close[i] > dayHigh ? close[i] : dayHigh;
                    dayLow = close[i] < dayLow && close[i] > 0 ? (close[i] != ReservedValues.EMPTY ? close[i] : dayLow) : dayLow;
                    intradayVol = intradayVol + Math.abs(close[i + 1] - close[i]);
                }
                s.setHighPrice(dayHigh,false);
                s.setLowPrice(dayLow,false);
                s.setTimeSeries(EnumBarSize.DAILY, time, new String[]{"intradayvol"}, new double[]{intradayVol});
            }
        }
        return s;
    }
}
