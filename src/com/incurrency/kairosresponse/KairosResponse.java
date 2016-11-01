/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.kairosresponse;

/**
 *
 * @author Pankaj
 */
public final class KairosResponse {
    public final Query queries[];

    public KairosResponse(Query[] queries){
        this.queries = queries;
    }

    public static final class Query {
        public final long sample_size;
        public final Result results[];

        public Query(long sample_size, Result[] results){
            this.sample_size = sample_size;
            this.results = results;
        }

        public static final class Result {
            public final String name;
            public final Group_by group_by[];
            public final Tags tags;
            public final Value values[];
    
            public Result(String name, Group_by[] group_by, Tags tags, Value[] values){
                this.name = name;
                this.group_by = group_by;
                this.tags = tags;
                this.values = values;
            }
    
            public static final class Group_by {
                public final String name;
                public final String type;
        
                public Group_by(String name, String type){
                    this.name = name;
                    this.type = type;
                }
            }
    
            public static final class Tags {
                public final String[] expiry;
                public final String[] option;
                public final String[] strike;
                public final String[] symbol;
        
                public Tags(String[] expiry, String[] option, String[] strike, String[] symbol){
                    this.expiry = expiry;
                    this.option = option;
                    this.strike = strike;
                    this.symbol = symbol;
                }
            }
    
            public static final class Value {
                public final long time;
                public final double price;
                
                public Value(long time,double price){
                    this.time = time;
                    this.price = price;
                }
            }
        }
    }
}
