/*
Copyright (C) 2011 Richard Gomes

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

 /*
 Copyright (C) 2002, 2003 Decillion Pty(Ltd)
 Copyright (C) 2005, 2006, 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.List;
import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.JDate;
import org.jquantlib.util.Pair;

/**
 * Term structure based on interpolation of zero yields
 * <p>
 * @category yieldtermstructures
 *
 * @author Richard Gomes
 *
 * @param <I> Interpolator
 */
public class InterpolatedZeroCurve<I extends Interpolator> extends ZeroYieldStructure implements Traits.Curve {
    //
    // static private methods
    //

    static private Interpolator constructInterpolator(final Class<?> klass) {
        if (klass == null) {
            throw new LibraryException("null interpolator"); // TODO: message
        }
        if (!Interpolator.class.isAssignableFrom(klass)) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }

        try {
            return (Interpolator) klass.newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator", e); // TODO: message
        }
    }

//XXX :: this method is defined in QuantLib/C++ but it is never called
//	public double[] zeroRates() {
//        return data();
//	}
    //
    // private fields
    //
    // TODO: all fields should be protected?  See: QL/C++
    private JDate[] dates;
    private /*@Time*/ double[] times;
    private Interpolation interpolation;
    private double[] data;

    //
    // private final fields
    //
    private final Class<I> classI;
    private final Interpolator interpolator;

    //
    // public constructors
    //
    public InterpolatedZeroCurve(
            final Class<I> classI,
            final JDate[] dates,
            final double[] yields,
            final DayCounter dc) {
        this(classI, dates, yields, dc, null, null);
    }

    public InterpolatedZeroCurve(
            final Class<I> classI,
            final JDate[] dates,
            final double[] yields,
            final DayCounter dc,
            final Calendar calendar) {
        this(classI, dates, yields, dc, calendar, null);
    }

    public InterpolatedZeroCurve(
            final Class<I> classI,
            final JDate[] dates,
            final double[] yields,
            final DayCounter dc,
            final Calendar calendar,
            final Interpolator interpolator) {
        super(dates[0], calendar == null ? new Calendar() : calendar, dc);

        QL.validateExperimentalMode();
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;

        QL.require(dates.length != 0, "Dates cannot be empty"); // TODO: message
        QL.require(yields.length != 0, "yields cannot be empty"); // TODO: message
        QL.require(dates.length == yields.length, "Dates must be the same size as yields"); // TODO: message
        QL.require(yields[0] == 1.0, "Initial discount factor must be 1.0"); // TODO: message

        this.dates = dates; // TODO: clone() ?
        this.data = yields; // TODO: clone() ?
        this.times = new double[dates.length];
        times[0] = 0.0;

        for (int i = 1; i < dates.length; ++i) {
            QL.require(dates[i].gt(dates[i - 1]), "Dates must be in ascending order"); // TODO: message
            QL.require(data[0] > 0, "Negative discount"); // TODO: message
            times[i] = dc.yearFraction(dates[0], dates[i]);
            QL.require(Closeness.isClose(times[i], times[i - 1]), "two dates correspond to the same time under this curve's day count convention"); // TODO: message
        }

        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
        this.interpolation = this.interpolator.interpolate(new Array(times), new Array(data));
        this.interpolation.update();
    }

    //
    // protected constructors
    //
    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final JDate referenceDate,
            final DayCounter dc) {
        this(classI, referenceDate, dc, null);
    }

    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final JDate referenceDate,
            final DayCounter dc,
            final Interpolator interpolator) {
        super(referenceDate, new Calendar(), dc);
        QL.validateExperimentalMode();
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final DayCounter dc) {
        this(classI, dc, null);
    }

    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final DayCounter dc,
            final Interpolator interpolator) {
        super(dc);

        QL.validateExperimentalMode();
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final /*@Natural*/ int settlementDays,
            final Calendar calendar,
            final DayCounter dc) {
        this(classI, settlementDays, calendar, dc, null);
    }

    protected InterpolatedZeroCurve(
            final Class<I> classI,
            final /*@Natural*/ int settlementDays,
            final Calendar calendar,
            final DayCounter dc,
            final Interpolator interpolator) {
        super(settlementDays, new Calendar(), dc);
        QL.validateExperimentalMode();

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    //
    // implements Traits.Curve
    //
    @Override
    public JDate maxDate() {
        final int last = dates.length - 1;
        return dates[last];
    }

    @Override
    public JDate[] dates() {
        return dates;
    }

    @Override
    public double[] times() {
        return times;
    }

    @Override
    public List<Pair<JDate, Double>> nodes() {
        final List<Pair<JDate, Double>> nodes = new ArrayList<Pair<JDate, Double>>();
        for (int i = 0; i < dates.length; ++i) {
            nodes.add(new Pair<JDate, Double>(dates[i], data[i]));
        }
        return nodes;
    }

    @Override
    public double[] data() {
        return data;
    }

    @Override
    public Interpolator interpolator() {
        return interpolator;
    }

    @Override
    public Interpolation interpolation() {
        return interpolation;
    }

    @Override
    public void setInterpolation(final Interpolation interpolation) {
        this.interpolation = interpolation;
    }

    @Override
    public void setDates(final JDate[] dates) {
        this.dates = dates; // TODO: clone() ?
    }

    @Override
    public void setTimes(final double[] times) {
        this.times = times; // TODO: clone() ?
    }

    @Override
    public void setData(final double[] data) {
        this.data = data; // TODO: clone() ?
    }

    @Override
    public double discount(final double t) {
        return discountImpl(t);
    }

    @Override
    public double forward(final double t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double zeroYield(final double t) {
        return zeroYieldImpl(t);
    }

    //
    // overrides ZeroYieldStructure
    //
    @Override
    protected double zeroYieldImpl(final double t) {
        return interpolation.op(t, true);
    }

}
