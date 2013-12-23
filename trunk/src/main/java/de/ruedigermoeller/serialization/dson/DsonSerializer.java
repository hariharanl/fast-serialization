package de.ruedigermoeller.serialization.dson;

import de.ruedigermoeller.serialization.FSTClazzInfo;
import de.ruedigermoeller.serialization.FSTConfiguration;

import java.lang.reflect.Array;
import java.util.Map;

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
 * Time: 14:25
 * To change this template use File | Settings | File Templates.
 */
public class DsonSerializer {

    protected DsonCharOutput out;
    protected DsonTypeMapper mapper;
    protected boolean pretty = true;
    protected boolean writeNull = false;

    public DsonSerializer(DsonCharOutput out, DsonTypeMapper mapper) {
        this.out = out;
        this.mapper = mapper;
    }

    public void writeObject(Object o) throws Exception {
        writeObjectInternal(o,0);
    }

    protected void writeObjectInternal(Object o, int indent) throws Exception {
        if ( o == null ) {
            out.writeString("null");
            return;
        }
        if ( o instanceof Number ) {
            out.writeString(o.toString());
            return;
        } else if ( o instanceof String ) {
            writeString((String)o);
            return;
        } else if ( o instanceof Map) {
        } else if ( o.getClass().isArray()) {
            writeArray(o,indent+1);
            return;
        } else {
            writeln();
            writeIndent(indent);
            String stringForType = mapper.getStringForType(o.getClass());
            out.writeString(stringForType);
            FSTClazzInfo clInfo = FSTConfiguration.getDefaultConfiguration().getCLInfoRegistry().getCLInfo(o.getClass());
            FSTClazzInfo.FSTFieldInfo[] fieldInfo = clInfo.getFieldInfo();
            for (int i = 0; i < fieldInfo.length; i++) {
                FSTClazzInfo.FSTFieldInfo fstFieldInfo = fieldInfo[i];
                Object fieldValue = fstFieldInfo.getObjectValue(o);
                fieldValue = mapper.coerceWriting(fieldValue);
                if ( fieldValue != null || writeNull ) {
                    writeln();
                    writeIndent(indent+1);
                    out.writeString(fstFieldInfo.getField().getName());
                    out.writeChar(':');
                    writeObjectInternal(fieldValue, indent + 2);
                }
            }
            out.writeChar(';');
        }
    }

    protected void writeln() {
        if ( pretty )
            out.writeChar('\n');
    }

    protected void writeArray(Object array, int indent) throws Exception {
        if ( array == null ) {
            writeObject(null);
            return;
        }
        int len = Array.getLength(array);
        out.writeChar('[');out.writeChar(' ');
        // fixme: this could be optimized for primitive arrays for speed
        boolean hadObj = false;
        for ( int ii=0; ii < len; ii++ ) {
            Object val = Array.get(array,ii);
            val = mapper.coerceWriting(val);
            if ( val instanceof Number || val instanceof String ) {
            } else {
                hadObj = true;
            }
            writeObjectInternal(val, indent);
            out.writeChar(' ');
        }
        out.writeChar(']');
    }

    public void writeString(String string) {
        if (string==null) {
            out.writeString("null");
            return;
        }
        if (string.length() == 0) {
            out.writeString("\"\"");
            return;
        }

        char         b;
        char         c = 0;
        int          i;
        int          len = string.length();
        String       t;

        out.writeChar('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':  out.writeChar('\\'); out.writeChar(c); break;
                case '/':
                    if (b == '<') {
                        out.writeChar('\\');
                    }
                    out.writeChar(c);
                    break;
                case '\b': out.writeString("\\b"); break;
                case '\t': out.writeString("\\t"); break;
                case '\n': out.writeString("\\n"); break;
                case '\f': out.writeString("\\f"); break;
                case '\r': out.writeString("\\r"); break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') ||
                            (c >= '\u2000' && c < '\u2100')) {
                        t = "000" + Integer.toHexString(c);
                        out.writeString("\\u" + t.substring(t.length() - 4));
                    } else {
                        out.writeChar(c);
                    }
            }
        }
        out.writeChar('"');
    }

    protected void writeIndent(int indent) {
        if ( pretty ) {
            for (int i=0; i<indent*2;i++) {
                out.writeChar(' ');
            }
        }
    }

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    public boolean isWriteNull() {
        return writeNull;
    }

    public void setWriteNull(boolean writeNull) {
        this.writeNull = writeNull;
    }
}
