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

package de.ruedigermoeller.serialization.serializers;

import de.ruedigermoeller.serialization.*;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 02.12.12
 * Time: 14:16
 * To change this template use File | Settings | File Templates.
 */
public class FSTDateSerializer extends FSTBasicObjectSerializer implements FSTCrossLanguageSerializer {
    @Override
    public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
        if ( out.getConf().isCrossLanguage() ) {
            out.writeCLong(((Date)toWrite).getTime());
        } else {
            out.writeFLong(((Date)toWrite).getTime());
        }
    }

    @Override
    public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        long l;
        if ( in.getConf().isCrossLanguage() ) {
            l = in.readCLong();
        } else {
            l = in.readFLong();
        }
        Object res = new Date(l);
        in.registerObject(res,streamPositioin,serializationInfo, referencee);
        return res;
    }

    @Override
    public Class getCrossLangLayout() {
        return Long.class;
    }
}
