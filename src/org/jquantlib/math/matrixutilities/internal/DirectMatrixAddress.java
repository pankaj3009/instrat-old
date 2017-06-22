/*
JQuantLib is Copyright (c) 2007, Richard Gomes

All rights reserved.

This source code is release under the BSD License.

JQuantLib includes code taken from QuantLib.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    Neither the names of the copyright holders nor the names of the QuantLib
    Group and its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package org.jquantlib.math.matrixutilities.internal;

import java.util.EnumSet;
import java.util.Set;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * This accessor provides contiguous addressing on matrices
 *
 * @see Matrix
 *
 * @author Richard Gomes
 */
public class DirectMatrixAddress extends DirectAddress implements Address.MatrixAddress {

    public DirectMatrixAddress(
            final double[] data,
            final int row0, final int row1,
            final Address chain,
            final int col0, final int col1,
            final Set<Address.Flags> flags,
            final boolean contiguous,
            final int rows, final int cols) {
        super(data, row0, row1, chain, col0, col1, flags, contiguous, rows, cols);
    }


    //
    // implements MatrixAddress
    //

    @Override
    public MatrixAddress toFortran() {
        return isFortran() ? this :
            new DirectMatrixAddress(data, row0, row1, this.chain, col0, col1, EnumSet.of(Address.Flags.FORTRAN), contiguous, rows, cols);
    }

    @Override
    public MatrixAddress toJava() {
        return isFortran() ?
            new DirectMatrixAddress(data, row0+1, row1+1, this.chain, col0+1, col1+1, EnumSet.noneOf(Address.Flags.class), contiguous, rows, cols)
            : this;
    }

    @Override
    public MatrixOffset offset() {
        return new DirectMatrixAddressOffset(offset, offset);
    }

    @Override
    public MatrixOffset offset(final int row, final int col) {
        return new DirectMatrixAddressOffset(row, col);
    }

    @Override
    public int op(final int row, final int col) {
        return (row0+row)*cols + (col0+col);
    }


    //
    // implements Cloneable
    //

    @Override
    public DirectMatrixAddress clone() {
        try {
            return (DirectMatrixAddress) super.clone();
        } catch (final Exception e) {
            throw new LibraryException(e);
        }
    }


    //
    // private inner classes
    //

    private class DirectMatrixAddressOffset extends DirectAddressOffset implements Address.MatrixAddress.MatrixOffset {

        public DirectMatrixAddressOffset(final int row, final int col) {
            super.row = row0+row;
            super.col = col0+col;
        }

        @Override
        public void nextRow() {
            super.row++;
        }

        @Override
        public void nextCol() {
            super.col++;
        }

        @Override
        public void prevRow() {
            super.row--;
        }

        @Override
        public void prevCol() {
            super.col--;
        }

        @Override
        public void setRow(final int row) {
            super.row = row0+row;
        }

        @Override
        public void setCol(final int col) {
            super.col = col0+col;
        }

        @Override
        public int op() {
            return row*cols + col;
        }

    }

}
