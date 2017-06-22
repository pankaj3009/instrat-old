
package com.incurrency.kairosresponse;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;

@Generated("org.jsonschema2pojo")
public class Tags {

    @SerializedName("expiry")
    @Expose
    private List<String> expiry = new ArrayList<String>();
    @SerializedName("option")
    @Expose
    private List<String> option = new ArrayList<String>();
    @SerializedName("strike")
    @Expose
    private List<String> strike = new ArrayList<String>();
    @SerializedName("symbol")
    @Expose
    private List<String> symbol = new ArrayList<String>();

    /**
     * 
     * @return
     *     The expiry
     */
    public List<String> getExpiry() {
        return expiry;
    }

    /**
     * 
     * @param expiry
     *     The expiry
     */
    public void setExpiry(List<String> expiry) {
        this.expiry = expiry;
    }

    /**
     * 
     * @return
     *     The option
     */
    public List<String> getOption() {
        return option;
    }

    /**
     * 
     * @param option
     *     The option
     */
    public void setOption(List<String> option) {
        this.option = option;
    }

    /**
     * 
     * @return
     *     The strike
     */
    public List<String> getStrike() {
        return strike;
    }

    /**
     * 
     * @param strike
     *     The strike
     */
    public void setStrike(List<String> strike) {
        this.strike = strike;
    }

    /**
     * 
     * @return
     *     The symbol
     */
    public List<String> getSymbol() {
        return symbol;
    }

    /**
     * 
     * @param symbol
     *     The symbol
     */
    public void setSymbol(List<String> symbol) {
        this.symbol = symbol;
    }

}
