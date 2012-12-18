/*
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *
 */
package de.ruedigermoeller.serialization;

import de.ruedigermoeller.serialization.serializers.*;
import de.ruedigermoeller.serialization.util.FSTObject2ObjectMap;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 10.11.12
 * Time: 15:04
 * To change this template use File | Settings | File Templates.
 */
public class FSTSerializerRegistry {

    public static FSTSerializerRegistry getMostCompatibleInstance() { // FIXME: move to config
        FSTSerializerRegistry res = new FSTSerializerRegistry();
        res.putSerializer(EnumSet.class, new FSTEnumSetSerializer(), true);
        return res;
    }


    static FSTObjectSerializer NULL = new NULLSerializer();

    static class NULLSerializer implements FSTObjectSerializer {
        @Override
        public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) {
        }

        @Override
        public void readObject(FSTObjectInput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) {
        }

        @Override
        public boolean willHandleClass(Class cl) {
            return true;
        }

        @Override
        public Object instantiate(Class objectClass, FSTObjectInput fstObjectInput, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) {
            return null;
        }
    };

    final static class SerEntry {
        boolean forSubClasses = false;
        FSTObjectSerializer ser;

        SerEntry(boolean forSubClasses, FSTObjectSerializer ser) {
            this.forSubClasses = forSubClasses;
            this.ser = ser;
        }
    }

    FSTObject2ObjectMap<Class,SerEntry> map = new FSTObject2ObjectMap<Class, SerEntry>(97);

    public final FSTObjectSerializer getSerializer(Class cl) {
        return getSerializer(cl,cl);
    }

    final FSTObjectSerializer getSerializer(Class cl, Class lookupStart) {
        final SerEntry serEntry = map.get(cl);
        if ( serEntry != null ) {
            if ( cl == lookupStart && serEntry.ser.willHandleClass(cl)) {
                return serEntry.ser;
            }
            if ( serEntry.forSubClasses && serEntry.ser.willHandleClass(cl) ) {
                putSerializer(lookupStart,serEntry.ser, false);
                return serEntry.ser;
            }
        }
        if ( cl != Object.class && cl != null ) {
            return getSerializer(cl.getSuperclass(),lookupStart);
        }
        return null;
    }

    public void putSerializer( Class cl, FSTObjectSerializer ser, boolean includeSubclasses) {
        map.put(cl,new SerEntry(includeSubclasses,ser));
    }


}