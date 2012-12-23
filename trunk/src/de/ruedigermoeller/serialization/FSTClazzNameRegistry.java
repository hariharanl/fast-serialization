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

import de.ruedigermoeller.serialization.util.FSTInt2ObjectMap;
import de.ruedigermoeller.serialization.util.FSTObject2IntMap;
import de.ruedigermoeller.serialization.util.FSTObject2ObjectMap;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 10.11.12
 * Time: 00:34
 * To change this template use File | Settings | File Templates.
 */
public class FSTClazzNameRegistry {

    static boolean ENABLE_SNIPPETS = true;
    private static final boolean DEBUG_CLNAMES = false;

    FSTObject2IntMap<Class> clzToId = new FSTObject2IntMap<Class>(13,false);
    FSTInt2ObjectMap idToClz = new FSTInt2ObjectMap(13);
    int classIdCount = 3;
    FSTClazzNameRegistry parent;

    // snippet stuff
    HashSet<Class> visitedClasses = new HashSet<Class>(13);
    FSTObject2IntMap<String> stringSnippets = new FSTObject2IntMap<String>(13,false);
    FSTInt2ObjectMap stringSnippetsReverse = new FSTInt2ObjectMap(13);
    int snippetCount = 3;

    private FSTConfiguration conf;

    public FSTClazzNameRegistry(FSTClazzNameRegistry par, FSTConfiguration conf) {
        parent = par;
        this.conf = conf;
        if ( parent != null ) {
            classIdCount = parent.classIdCount+1;
            snippetCount = parent.snippetCount+1;
        }
    }

    public void clear() {
        clzToId.clear();
        idToClz.clear();
        classIdCount = 3;
        visitedClasses.clear();
        stringSnippets.clear();
        stringSnippetsReverse.clear();
        snippetCount = 3;
        if ( parent != null ) {
            classIdCount = parent.classIdCount+1;
            snippetCount = parent.snippetCount+1;
        }
    }

    void registerClass( Class c ) {
        registerClass(c,false);
    }

    // for read => always increase handle (wg. replaceObject)
    void registerClass( Class c, boolean forRead ) {
        if ( clzToId.get(c) != Integer.MIN_VALUE ) {
            return;
        }
        addClassMapping(c, classIdCount++);
        Class pred[] = conf.getCLInfoRegistry().getCLInfo(c).getPredict();
        if ( pred != null ) {
            for (int i = 0; i < pred.length; i++) {
                Class aClass = pred[i];
                registerClass(aClass);
            }
        }
    }

    // for read => always increase handle (wg. replaceObject)
    public void registerClass( Class c, int code) {
        if ( clzToId.get(c) != Integer.MIN_VALUE ) {
            return;
        }
        addClassMapping(c, code);
    }

    protected void addClassMapping( Class c, int id ) {
        clzToId.put(c, id);
        idToClz.put(id, conf.getCLInfoRegistry().getCLInfo(c) );
    }

    private int getIdFromClazz(Class c) {
        int res = Integer.MIN_VALUE;
        if ( parent != null ) {
            res = parent.getIdFromClazz(c);
        }
        if ( res == Integer.MIN_VALUE ) {
            res = clzToId.get(c);
        }
        return res;
    }

    public void encodeClass(FSTObjectOutput out, Class c) throws IOException {
        int i = getIdFromClazz(c);
        if ( i != Integer.MIN_VALUE ) {
            out.writeCShort((short) i); // > 2 !!
        } else {
            if ( DEBUG_CLNAMES )
                System.out.println("write class name class:"+c.getName());
            int snippet = 0;
            if ( ENABLE_SNIPPETS ) {
                snippet = findLongestSnippet(c);
            }
            if ( snippet == 0 ) {
                out.writeCShort((short) 0); // no direct cl id
                out.writeStringUTF(c.getName());
                addCLNameSnippets(c);
                addCLNameSnippets( Array.newInstance(c,0).getClass() );
                if ( DEBUG_CLNAMES )
                    System.out.println("netto write:'" + c.getName() + "'");
            } else {
                out.writeCShort((short) 1); // no direct cl id, but snippet
                String snippetString = getSnippetFromId(snippet);
                out.writeCShort((short) snippet);
                String written = null;
                if ( c.getName().length() == snippetString.length() ) {
                    written = "";
                } else {
                    written = c.getName().substring(snippetString.length() + 1);
                }
                addCLNameSnippets(c);
                addCLNameSnippets( Array.newInstance(c,0).getClass() );
                out.writeStringUTF(written);
                if ( DEBUG_CLNAMES )
                    System.out.println("netto write:'"+written+"'");
            }
            registerClass(c, false);
        }
    }

    public FSTClazzInfo decodeClass(FSTObjectInput in) throws IOException, ClassNotFoundException {
        short c = in.readCShort();
        if ( c == 0 ) {
            // full class name
            String clName = in.readStringUTF();
            Class cl = Class.forName(clName);
            registerClass(cl, true);
            addCLNameSnippets(cl);
            addCLNameSnippets(Array.newInstance(cl, 0).getClass());
            return conf.getCLInfoRegistry().getCLInfo(cl);
        } else if ( c == 1 ) {
            int snippetId = in.readCShort();
            String snippetString = getSnippetFromId(snippetId);
            String clName = snippetString;
            String s = in.readStringUTF();
            if ( clName.length() > 0 && s.length() > 0 ) {
                clName += "."+ s;
            }
            Class cl = null;
            try {
                cl = classForName(clName);
            } catch ( ClassNotFoundException ex ) {
                String oldTry = clName;
                clName = snippetString;
                if ( clName.length() > 0 && s.length() > 0 ) {
                    clName += "$"+ s;
                }
                try {
                    cl = classForName(clName);
                    if ( parent != null ) {
                        parent.classCache.put(oldTry,cl);
                    }
                    classCache.put(oldTry,cl);
                } catch ( ClassNotFoundException ex1 ) {
                    throw ex;
                }

            }
            registerClass(cl, true);
            addCLNameSnippets(cl);
            addCLNameSnippets( Array.newInstance(cl,0).getClass() );
            return conf.getCLInfoRegistry().getCLInfo(cl);
        } else {
            FSTClazzInfo aClass = getClazzFromId(c);
            if ( aClass == null ) {
                throw new RuntimeException("unable to decode class from code "+c);
            }
            return aClass;
        }
    }

    FSTObject2ObjectMap<String,Class> classCache = new FSTObject2ObjectMap<String, Class>(200);
    private Class classForName(String clName) throws ClassNotFoundException {
        if ( parent != null ) {
            return parent.classForName(clName);
        }
        Class res = classCache.get(clName);
        if ( res == null ) {
            res = Class.forName(clName, false, conf.getClassLoader() );
            if ( res != null ) {
                classCache.put(clName, res);
            }
        }
        return res;
    }

    private String getSnippetFromId(int snippetId) {
        String res = null;
        if ( parent != null ) {
            res = parent.getSnippetFromId(snippetId);
        }
        if ( res == null )
            return (String) stringSnippetsReverse.get(snippetId);
        else
            return res;
    }

    private FSTClazzInfo getClazzFromId(int c) {
        FSTClazzInfo res = null;
        if ( parent != null ) {
            res = parent.getClazzFromId(c);
        }
        if ( res == null ) {
            return (FSTClazzInfo) idToClz.get(c);
        } else {
            return res;
        }
    }

    private int getStringId(String name) {
        return addSingleSnippet(name);
    }

    void addCLNameSnippets( Class cl ) {
        if ( visitedClasses.contains(cl) || ! ENABLE_SNIPPETS ) {
            return;
        }
        visitedClasses.add(cl);
        String pack = cl.getName();
        int idx = 1;
        while (idx > 0) {
            if ( getStringSnippet(pack) != Integer.MIN_VALUE ) {
                return;
            }
            addSingleSnippet(pack);
            if ( idx < 0 ) {
                idx = pack.lastIndexOf('$');
                idx = pack.lastIndexOf('.');
            }
            if ( idx > 0 ) {
                pack = pack.substring(0,idx);
            } else {
                return;
            }
        }
    }

    int findLongestSnippet( Class c ) {
        String pack = c.getName();
        int idx = 1;
        int id = 0;
        while (idx > 0) {
            id = getStringSnippet(pack);
            if ( id != Integer.MIN_VALUE ) {
                break;
            }
            idx = pack.lastIndexOf('$');
            if ( idx < 0 ) {
                idx = pack.lastIndexOf('.');
            }
            if ( idx > 0 ) {
                pack = pack.substring(0,idx);
            }
        }
        if ( id == Integer.MIN_VALUE ) {
            return 0;
        } else {
            return id;
        }
    }

    private int getStringSnippet(String pack) {
        int res = Integer.MIN_VALUE;
        if ( parent != null ) {
            res = parent.getStringSnippet(pack);
        }
        if ( res == Integer.MIN_VALUE )
            return stringSnippets.get(pack);
        else
            return res;
    }

    int addSingleSnippet(String pack) {
        int integer = getStringSnippet(pack);
        if (integer != Integer.MIN_VALUE) {
            return integer;
        }
        stringSnippets.put(pack, snippetCount);
        stringSnippetsReverse.put(snippetCount,pack);
        //System.out.println("SNIPPET:" + pack + " " + snippetCount);
        snippetCount++;
        return snippetCount-1;
    }

}
