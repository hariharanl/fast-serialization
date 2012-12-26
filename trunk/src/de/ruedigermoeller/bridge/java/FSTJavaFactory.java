package de.ruedigermoeller.bridge.java;

import de.ruedigermoeller.serialization.util.FSTInputStream;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 25.12.12
 * Time: 16:48
 * To change this template use File | Settings | File Templates.
 */
public abstract class FSTJavaFactory {

    public abstract Object instantiate(int clzId, FSTCountingInputStream in, FSTSerBase container, int streamPosition) throws IOException;
    public Object defaultInstantiate(Class clz, FSTCountingInputStream in, FSTSerBase container, int streampos) {
        return null;
    }

    public Object decodeFromStream(InputStream inputStream) throws IOException {
        FSTSerBase base = new FSTSerBase(this);
        FSTCountingInputStream in = new FSTCountingInputStream(inputStream);
        return base.decodeObject(in);
    }

}