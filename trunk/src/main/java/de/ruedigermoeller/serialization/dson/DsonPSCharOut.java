package de.ruedigermoeller.serialization.dson;

import java.io.PrintStream;

/**
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 20.12.13
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */

/**
 * implementation of charoutput on top of printstream
 */
public class DsonPSCharOut implements DsonCharOutput {
    PrintStream out;

    public DsonPSCharOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public void writeChar(char c) {
        out.print(c);
    }

    @Override
    public void writeString(String s) {
        out.print(s);
    }
}
