package de.ruedigermoeller.serialization.util;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 20.11.12
 * Time: 21:02
 * To change this template use File | Settings | File Templates.
 */
public class FSTInt2IntMap {
    public int  mKeys[];
    public int  mValues[];
    public int     mNumberOfElements;
    FSTInt2IntMap next;

    public FSTInt2IntMap(int initialSize)
    {
        if (initialSize < 2)
        {
            initialSize = 2;
        }

        initialSize = FSTObject2IntMap.adjustSize(initialSize*2);

        mKeys = new int[initialSize];
        mValues = new int[initialSize];
        mNumberOfElements = 0;
    }

    public int size()
    {
        return mNumberOfElements + (next != null ? next.size():0);
    }

    final public void put(int key, int value)
    {
        int hash = key & 0x7FFFFFFF;
        if ( (key == 0 && value == 0) || value == Integer.MIN_VALUE) {
            throw new RuntimeException("key value pair not supported "+key+" "+value);
        }
        //putHash(key, value, hash); inline ..
        if (mNumberOfElements*2 > mKeys.length)
        {
            resize(mKeys.length * 2);
        }

        int idx = hash % mKeys.length;

        final int mKey = mKeys[idx];
        if (mKey == 0 && mValues[idx] == 0 ) // new
        {
            mNumberOfElements++;
            mValues[idx] = value;
            mKeys[idx] = key;
        }
        else if (mKey == key )  // overwrite
        {
            mValues[idx] = value;
        } else {
            putNext(hash, key, value);
        }
        // end inline
    }

    final void putHash(int key, int value, int hash) {
        if (mNumberOfElements*2 > mKeys.length)
        {
            resize(mKeys.length * 2);
        }

        int idx = hash % mKeys.length;

        final int mKey = mKeys[idx];
        if (mKey == 0 && mValues[idx] == 0 ) // new
        {
            mNumberOfElements++;
            mValues[idx] = value;
            mKeys[idx] = key;
        }
        else if (mKey == key )  // overwrite
        {
            mValues[idx] = value;
        } else {
            putNext(hash, key, value);
        }
    }

    final void putNext(int hash, int key, int value) {
        if ( next == null ) {
            int newSiz = mKeys.length/12;
            next = new FSTInt2IntMap(newSiz);
        }
        next.putHash(key,value,hash);
    }

    final public int get(int key) {
        int hash = key & 0x7FFFFFFF;
        return getHash(key,hash);
    }

    final int getHash(int key, int hash)
    {
        final int idx = hash % mKeys.length;

        final int mKey = mKeys[idx];
        if (mKey == 0 && mValues[idx] == 0) // not found
        {
            return Integer.MIN_VALUE;
        }
        else if (mKey == key)  // found
        {
            return mValues[idx];
        } else {
            if ( next == null ) {
                return Integer.MIN_VALUE;
            }
            return next.getHash(key,hash);
        }
    }

    final void resize(int newSize)
    {
        newSize = FSTObject2IntMap.adjustSize(newSize);
        int[]    oldTabKey = mKeys;
        int[] oldTabVal = mValues;

        mKeys = new int[newSize];
        mValues           = new int[newSize];
        mNumberOfElements = 0;

        for (int n = 0; n < oldTabKey.length; n++)
        {
            if (oldTabKey[n] != 0 || oldTabVal[n] != 0)
            {
                put(oldTabKey[n], (int)oldTabVal[n]);
            }
        }
        if ( next != null ) {
            FSTInt2IntMap oldNext = next;
            next = null;
            oldNext.rePut(this);
        }
    }

    private void rePut(FSTInt2IntMap kfstObject2IntMap) {
        for (int i = 0; i < mKeys.length; i++) {
            int mKey = mKeys[i];
            if ( mKey != 0 || mValues[i] != 0  ) {
                kfstObject2IntMap.put(mKey,(int)mValues[i]);
            }
        }
        if ( next != null ) {
            next.rePut(kfstObject2IntMap);
        }
    }

    public void clear() {
        FSTUtil.clear(mKeys);
        FSTUtil.clear(mValues);
        mNumberOfElements = 0;
        if ( next != null ) {
            next.clear();
        }
    }

}