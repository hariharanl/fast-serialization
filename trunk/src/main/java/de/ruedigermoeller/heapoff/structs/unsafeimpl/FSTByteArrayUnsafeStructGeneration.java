package de.ruedigermoeller.heapoff.structs.unsafeimpl;

import de.ruedigermoeller.serialization.FSTClazzInfo;
import javassist.*;
import javassist.expr.FieldAccess;

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
 * Date: 22.06.13
 * Time: 20:54
 * To change this template use File | Settings | File Templates.
 */
public class FSTByteArrayUnsafeStructGeneration implements FSTStructGeneration {

    public static boolean trackChanges = true;

    @Override
    public FSTStructGeneration newInstance() {
        return new FSTByteArrayUnsafeStructGeneration();
    }

    @Override
    public void defineStructWriteAccess(FieldAccess f, CtClass type, FSTClazzInfo.FSTFieldInfo fieldInfo) {
        int off = fieldInfo.getStructOffset();
        try {
            boolean vola = fieldInfo.isVolatile();
            validateAnnotations(fieldInfo,vola);
            String insert = "";
            if ( vola ) {
                insert = "Volatile";
            }
            if ( type == CtPrimitiveType.booleanType ) {
                final String body = "___bytes.putBool" + insert + "((long)" + off + "+___offset, $1 );";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,1);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.byteType ) {
                final String body = "___bytes.put"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,1);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.charType ) {
                final String body = "___bytes.putChar"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,2);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.shortType ) {
                final String body = "___bytes.putShort"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,2);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.intType ) {
                final String body = "___bytes.putInt"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,4);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.longType ) {
                final String body = "___bytes.putLong"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,8);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.floatType ) {
                final String body = "___bytes.putFloat"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,4);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            if ( type == CtPrimitiveType.doubleType ) {
                final String body = "___bytes.putDouble"+insert+"("+off+"+___offset,$1);";
                if (trackChanges) {
                    f.replace("{" +
                            body +
                            "if (tracker!=null) tracker.addChange("+off+"+___offset,8);" +
                            "}");
                } else {
                    f.replace(body);
                }
            } else
            {
                String code =
                "{"+
                    "long tmpOff = ___offset + ___bytes.getInt("+off+" + ___offset);"+
                    "if ( $1 == null ) { " +
                        "___bytes.putInt(tmpOff+4,-1); " +
                        "return; " +
                    "}"+
                    "int obj_len=___bytes.getInt(tmpOff); "+
                    "de.ruedigermoeller.heapoff.structs.FSTStruct struct = (de.ruedigermoeller.heapoff.structs.FSTStruct)$1;"+
                    "if ( !struct.isOffHeap() ) {"+
                    "    struct=___fac.toStruct(struct);"+ // FIMXE: do direct toByte to avoid tmp alloc
                    "}"+
                    "if (struct.getByteSize() > obj_len ) throw new RuntimeException(\"object too large to be written\");"+
                    (trackChanges ? "if (tracker!=null) tracker.addChange(tmpOff,struct.getByteSize()); ":"") +
//                    "unsafe.copyMemory(struct.___bytes,struct.___offset,___bytes,tmpOff,(long)struct.getByteSize());"+
                    "struct.___bytes.copyTo(___bytes,tmpOff,struct.___offset,(long)struct.getByteSize());"+
                    "___bytes.putInt(tmpOff, obj_len);"+ // rewrite original size
                "}";
                f.replace(code);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    void validateAnnotations(FSTClazzInfo.FSTFieldInfo fieldInfo, boolean vola) {
        if ( vola ) {
            if ( ! fieldInfo.isIntegral() )
                throw new RuntimeException("@Volatile only applicable to primitive types");
        }
    }

    @Override
    public void defineArrayAccessor(FSTClazzInfo.FSTFieldInfo fieldInfo, FSTClazzInfo clInfo, CtMethod method) {
        boolean vola = fieldInfo.isVolatile();
        validateAnnotations(fieldInfo,vola);
        String insert = "";
        if ( vola ) {
            insert = "Volatile";
        }
        try {
            Class arrayType = fieldInfo.getArrayType();
            int off = fieldInfo.getStructOffset();
            String prefix ="{ long _st_off=___offset + ___bytes.getInt("+off+"+___offset);"+ // array base offset in byte arr
                    "int _st_len=___bytes.getInt("+off+"+4+___offset); "+
                    "if ($1>=_st_len||$1<0) throw new ArrayIndexOutOfBoundsException(\"index:\"+$1+\" len:\"+_st_len);";
            if ( method.getReturnType() == CtClass.voidType ) {
                String record = "";
                if ( arrayType == boolean.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1,1);";
                    }
                    method.setBody(prefix+"___bytes.putBool"+insert+"( _st_off+$1,$2);"+record+"}");
                } else
                if ( arrayType == byte.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange((long)_st_off+$1,1);";
                    }
                    method.setBody(prefix+"___bytes.put"+insert+"( _st_off+$1,$2);"+record+"}");
                } else
                if ( arrayType == char.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*2,2);";
                    }
                    method.setBody(prefix+"___bytes.putChar"+insert+"( _st_off+$1*2,$2);"+record+"}");
                } else
                if ( arrayType == short.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*2,2);";
                    }
                    method.setBody(prefix+" ___bytes.putShort"+insert+"( _st_off+$1*2,$2);"+record+"}");
                } else
                if ( arrayType == int.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*4,4);";
                    }
                    method.setBody(prefix+" ___bytes.putInt"+insert+"(_st_off+$1*4,$2);"+record+"}");
                } else
                if ( arrayType == long.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*8,8);";
                    }
                    method.setBody(prefix+"___bytes.putLong"+insert+"( _st_off+$1*8,$2);"+record+"}");
                } else
                if ( arrayType == double.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*8,8);";
                    }
                    method.setBody(prefix+"___bytes.putDouble"+insert+"( _st_off+$1*8,$2);"+record+"}");
                } else
                if ( arrayType == float.class ) {
                    if (trackChanges) {
                        record = "if (tracker!=null) tracker.addChange(_st_off+$1*4,4);";
                    }
                    method.setBody(prefix+"___bytes.putFloat"+insert+"(_st_off+$1*4,$2);"+record+"}");
                } else {
                    method.setBody(
                    prefix+
                        "int _elem_len=___bytes.getInt("+off+"+8+___offset); "+
                        "de.ruedigermoeller.heapoff.structs.FSTStruct struct = (de.ruedigermoeller.heapoff.structs.FSTStruct)$2;"+
                        "if ( struct == null ) { " +
                            "___bytes.putInt((long)_st_off+$1*_elem_len+4,-1); " +
                            "return; " +
                        "}"+
                        "if ( !struct.isOffHeap() ) {"+
                        "    struct=___fac.toStruct(struct);"+ // FIMXE: do direct toByte to avoid tmp alloc
                        "}"+
                        "if ( _elem_len < struct.getByteSize() )"+
                        "    throw new RuntimeException(\"Illegal size when rewriting object array value. elem size:\"+_elem_len+\" new object size:\"+struct.getByteSize()+\"\");"+
                        (trackChanges ? "if (tracker!=null) tracker.addChange(_st_off+$1*_elem_len, struct.getByteSize()); ":"") +
//                        "unsafe.copyMemory(struct.___bytes,struct.___offset,___bytes,(long)_st_off+$1*_elem_len,(long)struct.getByteSize());"+
                        "struct.___bytes.copyTo(___bytes,(long)_st_off+$1*_elem_len,struct.___offset,(long)struct.getByteSize());"+
                    "}"
                    );
                }
            } else { // read access
                if ( arrayType == boolean.class ) {
                    String src = prefix + "return ___bytes.getBool" + insert + "( (long)_st_off+$1); }";
                    method.setBody(src);
                } else
                if ( arrayType == byte.class ) {
                    method.setBody(prefix+"return ___bytes.get"+insert+"( (long)_st_off+$1);}");
                } else
                if ( arrayType == char.class ) {
                    method.setBody(prefix+"return ___bytes.getChar"+insert+"( (long)_st_off+$1*2); }");
                } else
                if ( arrayType == short.class ) {
                    method.setBody(prefix+"return ___bytes.getShort"+insert+"( (long)_st_off+$1*2);}");
                } else
                if ( arrayType == int.class ) {
                    method.setBody(prefix+"return ___bytes.getInt"+insert+"( (long)_st_off+$1*4);}");
                } else
                if ( arrayType == long.class ) {
                    method.setBody(prefix+"return ___bytes.getLong"+insert+"( (long)_st_off+$1*8);}");
                } else
                if ( arrayType == double.class ) {
                    method.setBody(prefix+"return ___bytes.getDouble"+insert+"( (long)_st_off+$1*8);}");
                } else
                if ( arrayType == float.class ) {
                    method.setBody(prefix+"return ___bytes.getFloat"+insert+"( (long)_st_off+$1*4);}");
                } else { // object array
                    String meth =
                    prefix+
                        "int _elem_len=___bytes.getInt("+off+"+8+___offset); "+
                        "return ("+fieldInfo.getArrayType().getName()+")___fac.getStructPointerByOffset(___bytes,(long)_st_off+$1*_elem_len);"+
                    "}";
                    method.setBody(meth);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void defineStructSetCAS(FSTClazzInfo.FSTFieldInfo casAcc, FSTClazzInfo clInfo, CtMethod method) {
        int off = casAcc.getStructOffset();
        try {
            if ( method.getParameterTypes().length != 2 ) {
                throw new RuntimeException("CAS setter requires expected and newValue args");
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        try {
            if ( casAcc.getType() == int.class ) {
                method.setBody("return ___bytes.compareAndSwapInt("+off+"+___offset,$1,$2);");
            } else
            if ( casAcc.getType() == int.class ) {
                method.setBody("return ___bytes.compareAndSwapLong("+off+"+___offset,$1,$2);");
            } else {
                throw new RuntimeException("CAS access only applicable to int and long.");
            }
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void defineArrayElementSize(FSTClazzInfo.FSTFieldInfo indexfi, FSTClazzInfo clInfo, CtMethod method) {
        int off = indexfi.getStructOffset();
        try {
            method.setBody("{ return ___bytes.getInt("+off+"+8+___offset); }");
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void defineArrayIndex(FSTClazzInfo.FSTFieldInfo fieldInfo, FSTClazzInfo clInfo, CtMethod method) {
        int index = fieldInfo.getStructOffset();
        try {
            method.setBody("{ return (int) (___bytes.getInt( ___offset+"+index+")+___offset); }");
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void defineArrayPointer(FSTClazzInfo.FSTFieldInfo indexfi, FSTClazzInfo clInfo, CtMethod method) {
        int index = indexfi.getStructOffset();
        CtClass[] parameterTypes = new CtClass[0];
        try {
            parameterTypes = method.getParameterTypes();
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        if ( parameterTypes != null && parameterTypes.length ==1 ) {
            try {
                if (indexfi.isIntegral()) {
                    method.setBody("{ ___fac.fillPrimitiveArrayBasePointer($1,___bytes, ___offset, "+index+"); }");
                } else {
                    method.setBody("{ ___fac.fillTypedArrayBasePointer($1,___bytes, ___offset, "+index+"); }");
                }
            } catch (CannotCompileException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                if (indexfi.isIntegral()) {
                    method.setBody("{ return (de.ruedigermoeller.heapoff.structs.FSTStruct)___fac.createPrimitiveArrayBasePointer(___bytes, ___offset, "+index+"); }");
                } else
                    method.setBody("{ return ("+indexfi.getArrayType().getName()+")___fac.createTypedArrayBasePointer(___bytes, ___offset, "+index+"); }");
            } catch (CannotCompileException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void defineArrayLength(FSTClazzInfo.FSTFieldInfo fieldInfo, FSTClazzInfo clInfo, CtMethod method) {
        int off = fieldInfo.getStructOffset();
        try {
            method.setBody("{ return ___bytes.getInt("+off+"+4+___offset); }");
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void defineFieldStructIndex(FSTClazzInfo.FSTFieldInfo fieldInfo, FSTClazzInfo clInfo, CtMethod method) {
        int off = fieldInfo.getStructOffset();
        try {
            method.setBody("{ return "+off+"; }");
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void defineStructReadAccess(FieldAccess f, CtClass type, FSTClazzInfo.FSTFieldInfo fieldInfo) {
        boolean vola = fieldInfo.isVolatile();
        validateAnnotations(fieldInfo,vola);
        String insert = "";
        if ( vola ) {
            insert = "Volatile";
        }
        int off = fieldInfo.getStructOffset();
        try {
            if ( type == CtPrimitiveType.booleanType ) {
                f.replace("$_ = ___bytes.getBool"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.byteType ) {
                f.replace("$_ = ___bytes.get"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.charType ) {
                f.replace("$_ = ___bytes.getChar"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.shortType ) {
                f.replace("$_ = ___bytes.getShort"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.intType ) {
                f.replace("$_ = ___bytes.getInt"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.longType ) {
                f.replace("$_ = ___bytes.getLong"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.floatType ) {
                f.replace("$_ = ___bytes.getFloat"+insert+"("+off+"+___offset);");
            } else
            if ( type == CtPrimitiveType.doubleType ) {
                f.replace("$_ = ___bytes.getDouble"+insert+"("+off+"+___offset);");
            } else { // object ref
                String typeString = type.getName();
                f.replace("{ int tmpIdx = ___bytes.getInt( "+off+" + ___offset); if (tmpIdx < 0) return null;" +
                        "long __tmpOff = ___offset + tmpIdx; " +
                        ""+typeString+" tmp = ("+ typeString +")___fac.getStructPointerByOffset(___bytes,__tmpOff); " +
                        "if ( tmp == null ) return null;"+
                        "tmp.tracker = tracker; " +
                        "$_ = tmp; " +
                        "}");
//                f.replace("{ Object _o = unsafe.toString(); $_ = _o; }");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
