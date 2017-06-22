/*
 Copyright (C) 2011 Tim Blackler

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.indexes;

import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;

/**
 * Daily Settlement Euribor index
 *
 * @author Tim Blackler
 */
public class DailyTenorEuribor365 extends IborIndex {

    //
    // public constructors
    //
    public DailyTenorEuribor365(final int settlementDays) {
        this(settlementDays, new Handle<YieldTermStructure>());
    }

    public DailyTenorEuribor365(final int settlementDays, final Handle<YieldTermStructure> h) {
        super("Euribor",
                new Period(1, TimeUnit.Days),
                settlementDays, // settlement days
                new EURCurrency(),
                new Target(),
                euriborConvention(new Period(1, TimeUnit.Days)),
                euriborEOM(new Period(1, TimeUnit.Days)),
                new Actual365Fixed(),
                h);
    }

}
