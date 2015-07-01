/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.pricing;

import org.apache.commons.math3.distribution.NormalDistribution;



/**
 *
 * @author pankaj
 */
public class BlackScholes {

    // Black-Scholes formula
    public static double callPrice(double S, double X, double r, double sigma, double T) {
        double d1 = (Math.log(S/X) + (r + sigma * sigma/2) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        return S * new NormalDistribution().cumulativeProbability(d1) - X * Math.exp(-r * T) * new NormalDistribution().cumulativeProbability(d2);
    }
    
    public static double putPrice(double S, double X, double r, double sigma, double T){
        double d1 = (Math.log(S/X) + (r + sigma * sigma/2) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);
        return X * Math.exp(-r * T)  * new NormalDistribution().cumulativeProbability(-d2) - S* new NormalDistribution().cumulativeProbability(-d1);
    }
    
    public static double callVol(double S, double X, double r, double price, double T){
        double seed=0.2;
        while(Math.abs(price-callPrice(S,X,r,seed,T))>0.05){
            //do newton
            seed=seed-(price-callPrice(S,X,r,seed,T))/(callPrice(S,X,r,seed+0.001,T)-callPrice(S,X,r,seed,T));
        }
        return seed;
    }
    
    public static double putVol(double S, double X, double r, double price, double T){
        double seed=0.2;
        while(Math.abs(price-putPrice(S,X,r,seed,T))>0.05){
            //do newton
            seed=seed-(price-putPrice(S,X,r,seed,T))/(putPrice(S,X,r,seed+0.001,T)-putPrice(S,X,r,seed,T));
        }
        return seed;
    }

    // estimate by Monte Carlo simulation
    public static double call(double S, double X, double r, double sigma, double T) {
        int N = 10000;
        double sum = 0.0;
        for (int i = 0; i < N; i++) {
            double eps = new java.util.Random().nextGaussian();
            double price = S * Math.exp(r*T - 0.5*sigma*sigma*T + sigma*eps*Math.sqrt(T));
            double value = Math.max(price - X, 0);
            sum += value;
        }
        double mean = sum / N;
     
        return Math.exp(-r*T) * mean;
    }

    // estimate by Monte Carlo simulation
    public static double call2(double S, double X, double r, double sigma, double T) {
        int N = 10000;
        double sum = 0.0;
        for (int i = 0; i < N; i++) {
            double price = S;
            double dt = T/10000.0;
            for (double t = 0; t <= T; t = t + dt) {
                price += r*price*dt +sigma*price*Math.sqrt(dt)*new java.util.Random().nextGaussian();
            }
            double value = Math.max(price - X, 0);
            sum += value;
        }
        double mean = sum / N;
     
        return Math.exp(-r*T) * mean;
    }

}
