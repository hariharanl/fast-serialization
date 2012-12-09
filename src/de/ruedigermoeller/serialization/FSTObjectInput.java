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

import de.ruedigermoeller.serialization.util.FSTInputStream;
import de.ruedigermoeller.serialization.util.FSTInt2ObjectMap;
import de.ruedigermoeller.serialization.util.FSTUtil;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Möller
 * Date: 04.11.12
 * Time: 11:53
 * To change this template use File | Settings | File Templates.
 */
public final class FSTObjectInput extends DataInputStream implements ObjectInput {

    public FSTClazzNameRegistry clnames;
    FSTObjectRegistry objects;

    Stack<String> debugStack;
    final static boolean DEBUGSTACK = false;
    int curDepth;

    ArrayList<CallbackEntry> callbacks;
    FSTConfiguration conf;
    FSTInputStream input;
    ConditionalCallback conditionalCallback;

    static class CallbackEntry {
        ObjectInputValidation cb;
        int prio;

        CallbackEntry(ObjectInputValidation cb, int prio) {
            this.cb = cb;
            this.prio = prio;
        }
    }

    public static interface ConditionalCallback {
        public boolean shouldSkip(Object halfDecoded, int streamPosition, Field field);
    }
    /**
     * Creates a DataInputStream that uses the specified
     * underlying InputStream.
     *
     * @param in the specified input stream
     */
    public FSTObjectInput(InputStream in, FSTConfiguration conf) throws IOException {
//        super(in);
//        input = new InputStreamWrapper(this.in);
        super(new FSTInputStream(in));
        input = (FSTInputStream) this.in;
        this.conf = conf;
        initRegistries();
    }

    void initRegistries() {
        objects = (FSTObjectRegistry) conf.getCachedObject(FSTObjectRegistry.class);
        if (objects == null) {
            objects = new FSTObjectRegistry(conf);
        } else {
            objects.clearForRead();
        }
        clnames = (FSTClazzNameRegistry) conf.getCachedObject(FSTClazzNameRegistry.class);
        if (clnames == null) {
            clnames = new FSTClazzNameRegistry(conf.getClassRegistry(), conf);
        } else {
            clnames.clear();
        }
        if (DEBUGSTACK) {
            debugStack = new Stack<String>();
        }
    }

    public ConditionalCallback getConditionalCallback() {
        return conditionalCallback;
    }

    public void setConditionalCallback(ConditionalCallback conditionalCallback) {
        this.conditionalCallback = conditionalCallback;
    }

    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        try {
            return readObject((Class[]) null);
        } catch (IllegalAccessException e) {
            dumpDebugStack();
            throw new IOException(e);
        } catch (InstantiationException e) {
            dumpDebugStack();
            throw new IOException(e);
        } catch (Throwable th) {
            dumpDebugStack();
            throw new IOException(th);
        }
    }

    void processValidation() throws InvalidObjectException {
        if (callbacks == null) {
            return;
        }
        Collections.sort(callbacks, new Comparator<CallbackEntry>() {
            @Override
            public int compare(CallbackEntry o1, CallbackEntry o2) {
                return o2.prio - o1.prio;
            }
        });
        for (int i = 0; i < callbacks.size(); i++) {
            CallbackEntry callbackEntry = callbacks.get(i);
            try {
                callbackEntry.cb.validateObject();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void dumpDebugStack() {
        if (DEBUGSTACK) {
            for (int i = 0; i < debugStack.size(); i++) {
                String s = debugStack.get(i);
                System.err.println(i + ":" + s);
            }
        }
    }

    public Object readObject(Class... possibles) throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException {
        if (curDepth != 0) {
            //System.out.println("Do not call this method from inside serialization");
        }
        curDepth++;
        try {
            if (possibles != null) {
                for (int i = 0; i < possibles.length; i++) {
                    Class possible = possibles[i];
                    clnames.registerClass(possible);
                    clnames.addCLNameSnippets(possible);
                }
            }
            Object res = readObjectInternal(possibles);
            processValidation();
            return res;
        } catch (Throwable th) {
            dumpDebugStack();
            throw new IOException(th);
        } finally {
            curDepth--;
        }
    }

    public Object readObjectInternal(Class... expected) throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException {
//        if ( curDepth == 0 ) {
//            throw new RuntimeException("do not call this directly. only for internal use (incl. Serializers)");
//        }
        try {
            FSTClazzInfo.FSTFieldInfo info = new FSTClazzInfo.FSTFieldInfo(expected, null, conf.getCLInfoRegistry().isIgnoreAnnotations());
            return readObjectWithHeader(info);
        } catch (Throwable t) {
            throw new IOException(t);
        }

    }

    Object readObjectWithHeader(FSTClazzInfo.FSTFieldInfo referencee) throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        final int readPos = input.pos;
        byte code = readFByte();
        Class c = null;
        switch (code) {
            case FSTObjectOutput.BIG_INT: {
                int val = readCInt();
                if (val >= 0 && val < FSTConfiguration.intObjects.length) {
                    return FSTConfiguration.intObjects[val];
                }
                return new Integer(val);
            }
            case FSTObjectOutput.BIG_BOOLEAN_FALSE: {
                return Boolean.FALSE;
            }
            case FSTObjectOutput.BIG_BOOLEAN_TRUE: {
                return Boolean.TRUE;
            }
            case FSTObjectOutput.NULL: {
                return null;
            }
            case FSTObjectOutput.HANDLE: {
                int handle = readCInt();
//                System.out.println("READ HANDLE "+handle+" "+referencee.getDesc());
                Object res = objects.getRegisteredObject(handle);
                if (res == null) {
                    throw new IOException("unable to ressolve handle " + handle + " " + referencee.getDesc() + " " + input.pos);
                }
                return res;
            }
            case FSTObjectOutput.COPYHANDLE: {
                int handle = readCInt(); // = streamposition
                Object res = objects.getRegisteredObject(handle);
                if (res == null) {
                    throw new IOException("unable to ressolve handle " + handle);
                }
                Object copy = copy(res, handle);
                //objects.registerObject(copy,true, readPos, null); //fixme:no need
                return copy;
            }
            case FSTObjectOutput.OBJECT_ARRAY: {
                Object res = readArray(referencee);
                if ( ! referencee.isFlat() ) {
                    objects.registerObjectForRead(res, readPos);
                }
                return res;
            }
            case FSTObjectOutput.TYPED: {
                c = referencee.getType();
                break;
            }
            case FSTObjectOutput.ENUM: {
                Class en = readClass();
                int ordinal = readCInt();
                Object res = en.getEnumConstants()[ordinal];
                if ( ! referencee.isFlat() ) {
                    objects.registerObjectForRead(res, readPos);
                }
                return res;
            }
            case FSTObjectOutput.OBJECT: {
                // class name
                c = readClass();
                break;
            }
            default:
                c = referencee.getPossibleClasses()[code - 1];
        }
        if (DEBUGSTACK) {
            debugStack.push("" + referencee.getDesc() + " code:" + code);
            debugStack.push("" + referencee.getDesc() + " " + c);
        }
        FSTClazzInfo serializationInfo = null;
        if ( referencee.lastInfo != null && referencee.lastInfo.clazz == c) {
            serializationInfo = referencee.lastInfo;
        } else {
            serializationInfo = conf.getCLInfoRegistry().getCLInfo(c);
        }
        try {
            Object newObj = null;
            FSTObjectSerializer ser = serializationInfo.getSer();
            boolean serInstance = false;
            if (ser != null) {
                newObj = ser.instantiate(c, this, serializationInfo, referencee, readPos);
            }
            if (newObj == null) {
                newObj = serializationInfo.newInstance();
            } else
                serInstance = true;
            if (newObj == null) {
                throw new IOException(referencee.getDesc() + ":Failed to instantiate '" + c.getName() + "'. Register a custom serializer implementing instantiate.");
            }
            if ( ! referencee.isFlat() ) {
                objects.registerObjectForRead(newObj, readPos);
            }
            if (newObj.getClass() != c) {//FIXME
                c = newObj.getClass();
                serializationInfo = conf.getCLInfoRegistry().getCLInfo(c);
            }
            if (ser != null) {
                if ( !serInstance )
                    ser.readObject(this, newObj, serializationInfo, referencee);
            } else if ( serializationInfo.isExternalizable() ) {
                ((Externalizable)newObj).readExternal(this);
            } else if (serializationInfo.useCompatibleMode()) {
                int pos = input.pos;
                Object replaced = readObjectCompatible(referencee, serializationInfo, newObj);
                if (replaced != null && replaced != newObj) {
                    objects.replace(newObj, replaced, pos);
                    newObj = replaced;
                }
            } else {
                FSTClazzInfo.FSTFieldInfo[] fieldInfo = serializationInfo.getFieldInfo();
                readObjectFields(referencee, serializationInfo, fieldInfo, newObj);
            }
            if (DEBUGSTACK) {
                debugStack.pop();
                debugStack.pop();
            }
            return newObj;
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        } catch (InstantiationException e) {
            throw new IOException(e);
        }
    }

    protected Object copy(Object res, int streamPosition) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        Object copy = conf.getCopier().copy(res, conf);
        if (copy == null) {
            return defaultCopy(res, streamPosition);
        }
        return copy;
    }

    ByteArrayOutputStream copyStream;
    FSTInt2ObjectMap<byte[]> mCopyHash;

    protected Object defaultCopy(Object toCopy, int streamPosition) throws IOException, ClassNotFoundException {
        final byte[] buf = input.buf;
        final int pos = streamPosition;
//        if ( mCopyHash == null ) {
//            mCopyHash = new FSTInt2ObjectMap<byte[]>(11);
//        }
//        buf = mCopyHash.get(streamPosition);
//        if ( buf == null ) {
//            if ( copyStream == null ) {
//                copyStream = new ByteArrayOutputStream(50);
//            }
//            copyStream.reset();
//            FSTObjectOutput fstObjectOutput = new FSTObjectOutput(copyStream, conf);
//            fstObjectOutput.objects.disabled = true;
//            fstObjectOutput.writeObject(toCopy, null);
//            fstObjectOutput.close();
//            buf = copyStream.toByteArray();
//            mCopyHash.put(streamPosition,buf);
//        }
        try {
            input.push(buf, pos, buf.length);
            Object res = readObject(null);
            input.pop();
            return res;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    protected Object readObjectCompatible(FSTClazzInfo.FSTFieldInfo referencee, FSTClazzInfo serializationInfo, Object newObj) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (FSTObjectOutput.DUMP) System.out.println("read compatible :" + newObj.getClass());
        Class cl = serializationInfo.getClazz();
        readObjectCompatibleRecursive(referencee, newObj, serializationInfo, cl);
        if (newObj != null &&
                serializationInfo.getReadResolveMethod() != null) {
            Object rep = null;
            try {
                rep = serializationInfo.getReadResolveMethod().invoke(newObj);
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            }
//                if (unshared && rep.getClass().isArray()) { //FIXME
//                    rep = cloneArray(rep);
//                }
//                if (rep != newObj) {
//                    handles.setObject(passHandle, obj = rep);
//                }
            System.out.println("READ RESSOLVE CALLED REPLACED " + newObj.getClass() + " by " + rep.getClass() + " pos:" + input.pos);
            newObj = rep;//FIXME: support this in call
        }
        return newObj;
    }

    protected void readObjectCompatibleRecursive(FSTClazzInfo.FSTFieldInfo referencee, Object toRead, FSTClazzInfo serializationInfo, Class cl) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        FSTClazzInfo.FSTCompatibilityInfo fstCompatibilityInfo = serializationInfo.compInfo.get(cl);
        if (!Serializable.class.isAssignableFrom(cl)) {
            return;
        }
        if (DEBUGSTACK) {
            debugStack.push("  compat:" + cl.getSimpleName());
        }
        readObjectCompatibleRecursive(referencee, toRead, serializationInfo, cl.getSuperclass());
        if (DEBUGSTACK) {
            debugStack.pop();
        }
        if (fstCompatibilityInfo != null && fstCompatibilityInfo.getReadMethod() != null) {
            try {
                ObjectInputStream objectInputStream = getObjectInputStream(cl, serializationInfo, referencee, toRead);
                fstCompatibilityInfo.getReadMethod().invoke(toRead, objectInputStream);
                fakeWrapper.pop();
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            }
        } else {
            if (fstCompatibilityInfo != null) {
                readObjectFields(referencee, serializationInfo, fstCompatibilityInfo.getFieldArray(), toRead);
            }
        }
    }

    protected void readObjectFields(FSTClazzInfo.FSTFieldInfo referencee, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo[] fieldInfo, Object newObj) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        int booleanMask = 0;
        int boolcount = 8;
        final int length = fieldInfo.length;
        int conditional = 0;
        for (int i = 0; i < length; i++) {
            try {
                FSTClazzInfo.FSTFieldInfo subInfo = fieldInfo[i];
                if (DEBUGSTACK) {
                    debugStack.push(subInfo.getDesc() + " " + newObj.getClass().getSimpleName());
                }
                if (FSTObjectOutput.DUMP) {
                    System.out.println("READFIELD " + fieldInfo[i].getField().getName());
                }
                if (subInfo.isIntegral() && !subInfo.isArray()) {
                    final Class subInfTzpe = subInfo.getType();
                    if (subInfTzpe == boolean.class) {
                        if (boolcount == 8) {
                            booleanMask = ((int) readFByte() + 256) &0xff;
                            boolcount = 0;
                        }
                        boolean val = (booleanMask & 128) != 0;
                        booleanMask = booleanMask << 1;
                        boolcount++;
                        serializationInfo.setBooleanValue(newObj, subInfo, val);
                    }
                    if (subInfTzpe == byte.class) {
                        serializationInfo.setByteValue(newObj, subInfo, readFByte());
                    } else if (subInfTzpe == char.class) {
                        serializationInfo.setCharValue(newObj, subInfo, readCChar());
                    } else if (subInfTzpe == short.class) {
                        serializationInfo.setShortValue(newObj, subInfo, readCShort());
                    } else if (subInfTzpe == int.class) {
                        serializationInfo.setIntValue(newObj, subInfo, readCInt());
                    } else if (subInfTzpe == double.class) {
                        serializationInfo.setDoubleValue(newObj, subInfo, readCDouble());
                    } else if (subInfTzpe == float.class) {
                        serializationInfo.setFloatValue(newObj, subInfo, readCFloat());
                    } else if (subInfTzpe == long.class) {
                        serializationInfo.setLongValue(newObj, subInfo, readCLong());
                    }
                } else {
                    if ( subInfo.isConditional() ) {
                        if ( conditional == 0 ) {
                            conditional = readFInt();
                            if ( skipConditional(newObj, conditional, subInfo) ) {
                                input.pos = conditional;
                                continue;
                            }
                        }
                    }
                    // object
                    Object subObject = readObjectWithHeader(subInfo);
                    serializationInfo.setObjectValue(newObj, subInfo, subObject);
                }
                if (DEBUGSTACK) {
                    debugStack.pop();
                }
            } catch (IllegalAccessException ex) {
                throw new IOException(ex);
            }
        }
    }

    private boolean skipConditional(Object newObj, int conditional, FSTClazzInfo.FSTFieldInfo subInfo) {
        if ( conditionalCallback != null ) {
            return conditionalCallback.shouldSkip(newObj,conditional,subInfo.getField());
        }
        return false;
    }

    protected void readCompatibleObjectFields(FSTClazzInfo.FSTFieldInfo referencee, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo[] fieldInfo, Map res) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        int booleanMask = 0;
        int boolcount = 8;
        for (int i = 0; i < fieldInfo.length; i++) {
            try {
                FSTClazzInfo.FSTFieldInfo subInfo = fieldInfo[i];
                if (FSTObjectOutput.DUMP) {
                    System.out.println("READFIELD " + fieldInfo[i].getField().getName());
                }
                if (subInfo.isIntegral() && !subInfo.isArray()) {
                    final Class subInfoType = subInfo.getType();
                    if (subInfoType == boolean.class) {
                        if (boolcount == 8) {
                            booleanMask = ((int) readFByte() + 256) &0xff;
                            boolcount = 0;
                        }
                        boolean val = (booleanMask & 128) != 0;
                        booleanMask = booleanMask << 1;
                        boolcount++;
                        res.put(subInfo.getField().getName(), val);
                    }
                    if (subInfoType == byte.class) {
                        res.put(subInfo.getField().getName(), readFByte());
                    } else if (subInfoType == char.class) {
                        res.put(subInfo.getField().getName(), readCChar());
                    } else if (subInfoType == short.class) {
                        res.put(subInfo.getField().getName(), readCShort());
                    } else if (subInfoType == int.class) {
                        res.put(subInfo.getField().getName(), readCInt());
                    } else if (subInfoType == double.class) {
                        res.put(subInfo.getField().getName(), readCDouble());
                    } else if (subInfoType == float.class) {
                        res.put(subInfo.getField().getName(), readCFloat());
                    } else if (subInfoType == long.class) {
                        res.put(subInfo.getField().getName(), readCLong());
                    }
                } else {
                    // object
                    Object subObject = readObjectWithHeader(subInfo);
                    res.put(subInfo.getField().getName(), subObject);
                }
                if (DEBUGSTACK) {
                    debugStack.pop();
                }
            } catch (IllegalAccessException ex) {
                throw new IOException(ex);
            }
        }
    }

    public String readStringCompressed() throws IOException {
        int len = readCInt();
        if (charBuf == null || charBuf.length < len * 3) {
            charBuf = new char[len * 3];
        }
        input.ensureReadAhead(len * 3);
        byte buf[] = input.buf;
        int count = input.pos;
        int chcount = 0;
        while ( chcount < len ) {
            char head = (char) ((buf[count++] + 256) &0xff);
            if (head >= 0 && head < 254) {
                charBuf[chcount++] = head;
            } else {
                if ( head == 254 ) {
                    int nibbles = ((buf[count++] + 256) &0xff);
                    for ( int ii = 0; ii < nibbles; ii++) {
                        int bufVal = ((buf[count]+256)&0xff);
                        if ((ii&1) == 0 ) {
                            charBuf[chcount++] = FSTObjectOutput.enc.charAt(bufVal &0xf);
                            if (ii==nibbles-1) {
                                count++;
                            }
                        } else {
                            charBuf[chcount++] = FSTObjectOutput.enc.charAt((bufVal>>>4)&0xf);
                            count++;
                        }
                    }
                } else {
                    int ch1 = ((buf[count++] + 256) &0xff);
                    int ch2 = ((buf[count++] + 256) &0xff);
                    charBuf[chcount++] = (char) ((ch1 << 8) + (ch2 << 0));
                }
            }
        }
        input.pos = count;
        return new String(charBuf, 0, chcount);
    }

    char[] charBuf;
    public String readStringUTF() throws IOException {
        int len = readCInt();
        if (charBuf == null || charBuf.length < len * 3) {
            charBuf = new char[len * 3];
        }
        input.ensureReadAhead(len * 3);
        byte buf[] = input.buf;
        int count = input.pos;
        int chcount = 0;
        for (int i = 0; i < len; i++) {
            char head = (char) ((buf[count++] + 256) &0xff);
            if (head >= 0 && head < 255) {
                charBuf[chcount++] = head;
            } else {
                int ch1 = ((buf[count++] + 256) &0xff);
                int ch2 = ((buf[count++] + 256) &0xff);
                charBuf[chcount++] = (char) ((ch1 << 8) + (ch2 << 0));
            }
        }
        input.pos = count;
        return new String(charBuf, 0, chcount);
    }

    protected Object readArray(FSTClazzInfo.FSTFieldInfo referencee) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        final int len = readCInt();
        if (len == -1) {
            return null;
        }
        Class arrCl = readClass();
        Class arrType = arrCl.getComponentType();
        if (!arrCl.getComponentType().isArray()) {
            Object array = Array.newInstance(arrType, len);
            objects.registerObjectForRead(array, input.pos);
            if (arrCl.getComponentType().isPrimitive()) {
                if (arrType == byte.class) {
                    byte[] arr = (byte[]) array;
                    read(arr);
                } else if (arrType == char.class) {
                    char[] arr = (char[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readCChar();
                    }
                } else if (arrType == short.class) {
                    short[] arr = (short[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readShort();
                    }
                } else if (arrType == int.class) {
                    final int[] arr = (int[]) array;
                    if ( referencee.isThin() ) {
                        for (int i = 0; i < len; i++) {
                            final int index = readCInt();
                            if ( index == len ) {
                                break;
                            }
                            final int val = readCInt();
                            arr[index] = val;
                        }
                    } else {
                        readCIntArr(len, arr);
                    }
                } else if (arrType == float.class) {
                    float[] arr = (float[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readFloat();
                    }
                } else if (arrType == double.class) {
                    double[] arr = (double[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readDouble();
                    }
                } else if (arrType == long.class) {
                    long[] arr = (long[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readLong();
                    }
                } else if (arrType == boolean.class) {
                    boolean[] arr = (boolean[]) array;
                    for (int j = 0; j < len; j++) {
                        arr[j] = readBoolean();
                    }
                } else {
                    throw new RuntimeException("unexpected primitive type " + arrType);
                }
            } else {
                Object arr[] = (Object[]) array;
                if ( referencee.isThin() ) {
                    for (int i = 0; i < len; i++) {
                        int idx = readCInt();
                        if ( idx == len ) {
                            break;
                        }
                        Object subArray = readObjectWithHeader(referencee);
                        arr[idx] = subArray;
                    }
                } else
                {
                    for (int i = 0; i < len; i++) {
                        Object value = readObjectWithHeader(referencee);
                        arr[i] = value;
                    }
                }

//                Class[] possibleClasses = null;
//                if ( referencee.getPossibleClasses() == null ) {
//                    possibleClasses = new Class[5];
//                } else {
//                    possibleClasses = Arrays.copyOf(referencee.getPossibleClasses(), referencee.getPossibleClasses().length + 5);
//                }
//                FSTClazzInfo.FSTFieldInfo newFI = new FSTClazzInfo.FSTFieldInfo(false, possibleClasses, null);
//                for ( int i=0; i < len; i++ ) {
//                    Object value = readObjectWithHeader(newFI);
//                    Array.set(array, i, value);
//                    if ( value != null ) {
//                        newFI.setPossibleClasses( FSTObjectOutput.addToPredictionArray(newFI.getPossibleClasses(), value.getClass()));
//                    }
//                }
            }
            return array;
        } else {
            Object array[] = (Object[]) Array.newInstance(arrType, len);
            if (!FSTUtil.isPrimitiveArray(arrType) && ! referencee.isFlat() ) {
                objects.registerObjectForRead(array, input.pos);
            }
//            if ( false && referencee.isThin() ) {
//                for (int i = 0; i < len; i++) {
//                    int idx = readCInt();
//                    if ( idx == len ) {
//                        break;
//                    }
//                    Object subArray = readArray(new FSTClazzInfo.FSTFieldInfo(referencee.getPossibleClasses(), null, conf.getCLInfoRegistry().isIgnoreAnnotations()));
//                    array[idx] = subArray;
//                }
//            } else
            {
                for (int i = 0; i < len; i++) {
                    Object subArray = readArray(new FSTClazzInfo.FSTFieldInfo(referencee.getPossibleClasses(), null, conf.getCLInfoRegistry().isIgnoreAnnotations()));
                    array[i] = subArray;
                }
            }
            return array;
        }
    }

    public void registerObject(Object o, int streamPosition, FSTClazzInfo info, FSTClazzInfo.FSTFieldInfo referencee) {
        if (!referencee.isFlat()) {
            objects.registerObjectForRead(o, streamPosition);
        }
    }

    public Class readClass() throws IOException, ClassNotFoundException {
        return clnames.decodeClass(this);
    }

    public int _readCInt() throws IOException {
        byte head = readFByte();
        // -128 = short byte, -127 == 4 byte
        if (head > -127 && head <= 127) {
            return head;
        }
        if (head == -128) {
            return readShort();
        } else {
            return readFInt();
        }
    }

    public void reset() throws IOException {
        input.reset();
    }

    public void resetForReuse(InputStream in) throws IOException {
        input.reset();
        this.in = in;
        input.initFromStream(in);
        objects.clearForRead(); clnames.clear();
    }

    public final int readFInt() throws IOException {
        input.ensureReadAhead(5);
        int count = input.pos;
        final byte buf[] = input.buf;
        int ch1 = (buf[count++]+256)&0xff;
        int ch2 = (buf[count++]+256)&0xff;
        int ch3 = (buf[count++]+256)&0xff;
        int ch4 = (buf[count++]+256)&0xff;
        input.pos = count;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    private void readCIntArr(int len, int[] arr) throws IOException {
        input.ensureReadAhead(5 * len);
        final byte buf[] = input.buf;
        int count = input.pos;
        for (int j = 0; j < len; j++) {
            final byte head = buf[count++];
            // -128 = short byte, -127 == 4 byte
            if (head > -127 && head <= 127) {
                arr[j] = head;
                continue;
            }
            if (head == -128) {
                final int ch1 = (buf[count++]+256)&0xff;
                final int ch2 = (buf[count++]+256)&0xff;
                arr[j] = (short)((ch1 << 8) + (ch2 << 0));
                continue;
            } else {
                int ch1 = (buf[count++]+256)&0xff;
                int ch2 = (buf[count++]+256)&0xff;
                int ch3 = (buf[count++]+256)&0xff;
                int ch4 = (buf[count++]+256)&0xff;
                arr[j] = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
            }
        }
        input.pos = count;
    }

    public int readCInt() throws IOException {
        input.ensureReadAhead(5);
        final byte buf[] = input.buf;
        int count = input.pos;
        final byte head = buf[count++];
        // -128 = short byte, -127 == 4 byte
        if (head > -127 && head <= 127) {
            input.pos = count;
            return head;
        }
        if (head == -128) {
            final int ch1 = (buf[count++]+256)&0xff;
            final int ch2 = (buf[count++]+256)&0xff;
            input.pos = count;
            return (short)((ch1 << 8) + (ch2 << 0));
        } else {
            int ch1 = (buf[count++]+256)&0xff;
            int ch2 = (buf[count++]+256)&0xff;
            int ch3 = (buf[count++]+256)&0xff;
            int ch4 = (buf[count++]+256)&0xff;
            input.pos = count;
            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        }
    }

    public final byte readFByte() {
        input.ensureReadAhead(1);
        return input.buf[input.pos++];
    }

    public long readFLong() throws IOException {
        return readLong();
    }

    public long readCLong() throws IOException {
        byte head = readFByte();
        // -128 = short byte, -127 == 4 byte
        if (head > -126 && head <= 127) {
            return head;
        }
        if (head == -128) {
            return readShort();
        } else if (head == -127) {
            return readFInt();
        } else {
            return readLong();
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        conf.returnObject(objects, clnames);
    }

    public char readCChar() throws IOException {
        char head = (char) ((readFByte() + 256) &0xff);
        // -128 = short byte, -127 == 4 byte
        if (head >= 0 && head < 255) {
            return head;
        }
        return readChar();
    }

    /**
     * Reads a 4 byte float.
     */
    public float readCFloat() throws IOException {
        return Float.intBitsToFloat(readFInt());
    }

    /**
     * Reads an 8 bytes double.
     */
    public double readCDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public short readCShort() throws IOException {
        int head = ((int) readFByte() + 256) &0xff;
        // -128 = short byte, -127 == 4 byte
        if (head >= 0 && head < 255) {
            return (short) head;
        }
        return readShort();
    }

    ////////////////////////////////////////////////////// epic compatibility hack /////////////////////////////////////////////////////////

    MyObjectStream fakeWrapper; // some jdk classes hash for ObjectStream, so provide the same instance always

    ObjectInputStream getObjectInputStream(final Class cl, final FSTClazzInfo clInfo, final FSTClazzInfo.FSTFieldInfo referencee, final Object toRead) throws IOException {
        ObjectInputStream wrapped = new ObjectInputStream() {
            @Override
            public Object readObjectOverride() throws IOException, ClassNotFoundException {
                try {
                    return FSTObjectInput.this.readObjectInternal(referencee.getPossibleClasses());
                } catch (IllegalAccessException e) {
                    throw new IOException(e);
                } catch (InstantiationException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public Object readUnshared() throws IOException, ClassNotFoundException {
                try {
                    return FSTObjectInput.this.readObjectInternal(referencee.getPossibleClasses()); // fixme
                } catch (IllegalAccessException e) {
                    throw new IOException(e);
                } catch (InstantiationException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public void defaultReadObject() throws IOException, ClassNotFoundException {
                try {
                    FSTObjectInput.this.readObjectFields(referencee, clInfo, clInfo.compInfo.get(cl).getFieldArray(), toRead); // FIXME: only fields of current class
                } catch (IllegalAccessException e) {
                    throw new IOException(e);
                } catch (InstantiationException e) {
                    throw new IOException(e);
                }
            }

            HashMap<String, Object> fieldMap;

            @Override
            public GetField readFields() throws IOException, ClassNotFoundException {
                try {
                    FSTClazzInfo.FSTCompatibilityInfo fstCompatibilityInfo = clInfo.compInfo.get(cl);
                    if (fstCompatibilityInfo.isAsymmetric()) {
                        fieldMap = new HashMap<String, Object>();
                        FSTObjectInput.this.readCompatibleObjectFields(referencee, clInfo, fstCompatibilityInfo.getFieldArray(), fieldMap);
                    } else {
                        fieldMap = (HashMap<String, Object>) FSTObjectInput.this.readObjectInternal(HashMap.class);
                    }
                } catch (IllegalAccessException e) {
                    throw new IOException(e);
                } catch (InstantiationException e) {
                    throw new IOException(e);
                }
                return new GetField() {
                    @Override
                    public ObjectStreamClass getObjectStreamClass() {
                        return ObjectStreamClass.lookup(cl);
                    }

                    @Override
                    public boolean defaulted(String name) throws IOException {
                        return fieldMap.get(name) == null;
                    }

                    @Override
                    public boolean get(String name, boolean val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Boolean) fieldMap.get(name)).booleanValue();
                    }

                    @Override
                    public byte get(String name, byte val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Byte) fieldMap.get(name)).byteValue();
                    }

                    @Override
                    public char get(String name, char val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Character) fieldMap.get(name)).charValue();
                    }

                    @Override
                    public short get(String name, short val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Short) fieldMap.get(name)).shortValue();
                    }

                    @Override
                    public int get(String name, int val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Integer) fieldMap.get(name)).intValue();
                    }

                    @Override
                    public long get(String name, long val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Long) fieldMap.get(name)).longValue();
                    }

                    @Override
                    public float get(String name, float val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Float) fieldMap.get(name)).floatValue();
                    }

                    @Override
                    public double get(String name, double val) throws IOException {
                        if (fieldMap.get(name) == null) {
                            return val;
                        }
                        return ((Double) fieldMap.get(name)).doubleValue();
                    }

                    @Override
                    public Object get(String name, Object val) throws IOException {
                        Object res = fieldMap.get(name);
                        if (res == null) {
                            return val;
                        }
                        return res;
                    }
                };
            }

            @Override
            public void registerValidation(ObjectInputValidation obj, int prio) throws NotActiveException, InvalidObjectException {
                if (callbacks == null) {
                    callbacks = new ArrayList<CallbackEntry>();
                }
                callbacks.add(new CallbackEntry(obj, prio));
            }

            @Override
            public int read() throws IOException {
                return FSTObjectInput.this.read();
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                return FSTObjectInput.this.read(buf, off, len);
            }

            @Override
            public int available() throws IOException {
                return FSTObjectInput.this.available();
            }

            @Override
            public void close() throws IOException {
            }

            @Override
            public boolean readBoolean() throws IOException {
                return FSTObjectInput.this.readBoolean();
            }

            @Override
            public byte readByte() throws IOException {
                return FSTObjectInput.this.readFByte();
            }

            @Override
            public int readUnsignedByte() throws IOException {
                return FSTObjectInput.this.readUnsignedByte();
            }

            @Override
            public char readChar() throws IOException {
                return FSTObjectInput.this.readChar();
            }

            @Override
            public short readShort() throws IOException {
                return FSTObjectInput.this.readShort();
            }

            @Override
            public int readUnsignedShort() throws IOException {
                return FSTObjectInput.this.readUnsignedShort();
            }

            @Override
            public int readInt() throws IOException {
                return FSTObjectInput.this.readFInt();
            }

            @Override
            public long readLong() throws IOException {
                return FSTObjectInput.this.readLong();
            }

            @Override
            public float readFloat() throws IOException {
                return FSTObjectInput.this.readFloat();
            }

            @Override
            public double readDouble() throws IOException {
                return FSTObjectInput.this.readDouble();
            }

            @Override
            public void readFully(byte[] buf) throws IOException {
                FSTObjectInput.this.readFully(buf);
            }

            @Override
            public void readFully(byte[] buf, int off, int len) throws IOException {
                FSTObjectInput.this.readFully(buf, off, len);
            }

            @Override
            public int skipBytes(int len) throws IOException {
                return FSTObjectInput.this.skipBytes(len);
            }

            @Override
            public String readUTF() throws IOException {
                return FSTObjectInput.this.readUTF();
            }

            @Override
            public String readLine() throws IOException {
                return FSTObjectInput.this.readLine();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return FSTObjectInput.this.read(b);
            }

            @Override
            public long skip(long n) throws IOException {
                return FSTObjectInput.this.skip(n);
            }

            @Override
            public void mark(int readlimit) {
                FSTObjectInput.this.mark(readlimit);
            }

            @Override
            public void reset() throws IOException {
                FSTObjectInput.this.reset();
            }

            @Override
            public boolean markSupported() {
                return FSTObjectInput.this.markSupported();
            }
        };
        if ( fakeWrapper == null ) {
            fakeWrapper = new MyObjectStream();
        }
        fakeWrapper.push(wrapped);
        return fakeWrapper;
    }

    static class MyObjectStream extends ObjectInputStream {

        ObjectInputStream wrapped;
        ObjectInputStream wrappedArr[] = new ObjectInputStream[30]; // if this is not sufficient use another lib ..
        int idx = 0;

        public void push( ObjectInputStream in ) {
            wrappedArr[idx++] = in;
            wrapped = in;
        }

        public void pop() {
            idx--;
            wrapped = wrappedArr[idx];
        }

        MyObjectStream() throws IOException, SecurityException {
            this.wrapped = wrapped;
        }

        @Override
        public Object readObjectOverride() throws IOException, ClassNotFoundException {
            return wrapped.readObject();
        }

        @Override
        public Object readUnshared() throws IOException, ClassNotFoundException {
            return wrapped.readUnshared();
        }

        @Override
        public void defaultReadObject() throws IOException, ClassNotFoundException {
            wrapped.defaultReadObject();
        }

        @Override
        public ObjectInputStream.GetField readFields() throws IOException, ClassNotFoundException {
            return wrapped.readFields();
        }

        @Override
        public void registerValidation(ObjectInputValidation obj, int prio) throws NotActiveException, InvalidObjectException {
            wrapped.registerValidation(obj,prio);
        }

        @Override
        public int read() throws IOException {
            return wrapped.read();
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return wrapped.read(buf, off, len);
        }

        @Override
        public int available() throws IOException {
            return wrapped.available();
        }

        @Override
        public void close() throws IOException {
            wrapped.close();
        }

        @Override
        public boolean readBoolean() throws IOException {
            return wrapped.readBoolean();
        }

        @Override
        public byte readByte() throws IOException {
            return wrapped.readByte();
        }

        @Override
        public int readUnsignedByte() throws IOException {
            return wrapped.readUnsignedByte();
        }

        @Override
        public char readChar() throws IOException {
            return wrapped.readChar();
        }

        @Override
        public short readShort() throws IOException {
            return wrapped.readShort();
        }

        @Override
        public int readUnsignedShort() throws IOException {
            return wrapped.readUnsignedShort();
        }

        @Override
        public int readInt() throws IOException {
            return wrapped.readInt();
        }

        @Override
        public long readLong() throws IOException {
            return wrapped.readLong();
        }

        @Override
        public float readFloat() throws IOException {
            return wrapped.readFloat();
        }

        @Override
        public double readDouble() throws IOException {
            return wrapped.readDouble();
        }

        @Override
        public void readFully(byte[] buf) throws IOException {
            wrapped.readFully(buf);
        }

        @Override
        public void readFully(byte[] buf, int off, int len) throws IOException {
            wrapped.readFully(buf, off, len);
        }

        @Override
        public int skipBytes(int len) throws IOException {
            return wrapped.skipBytes(len);
        }

        @Override
        public String readUTF() throws IOException {
            return wrapped.readUTF();
        }

        @Override
        public String readLine() throws IOException {
            return wrapped.readLine();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return wrapped.read(b);
        }

        @Override
        public long skip(long n) throws IOException {
            return wrapped.skip(n);
        }

        @Override
        public void mark(int readlimit) {
            wrapped.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            wrapped.reset();
        }

        @Override
        public boolean markSupported() {
            return wrapped.markSupported();
        }
    }

}