package de.ruedigermoeller.heapofftest.gcbenchmarks;

/**
 * Created with IntelliJ IDEA.
 * User: ruedi
 * Date: 22.07.13
 * Time: 14:45
 * To change this template use File | Settings | File Templates.
 */
public class ObjRefCost extends BasicGCBench {

    static class GCWrapper {
        GCWrapper(Object referenced) {
            this.referenced = referenced;
        }

        Object referenced;
    }

    static class GCWrapperMiddle {
        int a,b,c,d,e,f,g,h,i,j,k,l;
        GCWrapperMiddle(Object referenced) {
            this.referenced = referenced;
        }

        Object referenced;
    }

    static class GCWrapperFat {
        int a,b,c,d,e,f,g,h,i,j,k,l;
        long aa,ab,ac,ad,ae,af,ag,ah,ai,jj,ak,al;
        GCWrapperFat(Object referenced) {
            this.referenced = referenced;
        }

        Object referenced;
    }

    static Object allocateWRPArr(int len) {
        Object res[] = new Object[len/2];
        for (int i = 0; i < res.length; i++) {
            res[i] = new GCWrapper(new GCWrapper(null));
        }
        return res;
    }

    static Object allocateWRPArrMiddle(int len) {
        Object res[] = new Object[len/2];
        for (int i = 0; i < res.length; i++) {
            res[i] = new GCWrapperMiddle(new GCWrapperMiddle(null));
        }
        return res;
    }

    static Object allocateWRPArrLarge(int len) {
        Object res[] = new Object[len/2];
        for (int i = 0; i < res.length; i++) {
            res[i] = new GCWrapperFat(new GCWrapperFat(null));
        }
        return res;
    }

    static Object allocateWRPLinked(int len) {
        GCWrapper res = new GCWrapper(null);
        for ( int i = 0; i < len; i++ ) {
            res = new GCWrapper(res);
        }
        return res;
    }

    static Object allocateWRPLinkedMiddle(int len) {
        GCWrapperMiddle res = new GCWrapperMiddle(null);
        for ( int i = 0; i < len; i++ ) {
            res = new GCWrapperMiddle(res);
        }
        return res;
    }

    static Object allocateWRPLinkedLarge(int len) {
        GCWrapperFat res = new GCWrapperFat(null);
        for ( int i = 0; i < len; i++ ) {
            res = new GCWrapperFat(res);
        }
        return res;
    }


    private static void benchLinked(int siz) {
        System.out.println("linked size:"+siz);
        Object res = allocateWRPLinked(siz);
        System.out.println("small "+benchFullGC());
        res = allocateWRPLinkedMiddle(siz);
        System.out.println("middle " + benchFullGC());
        res = allocateWRPLinkedLarge(siz);
        System.out.println("large " + benchFullGC());
        if ( res.equals("no") )
            System.out.println("bla");
    }

    private static void benchBytee(int siz) {
        System.out.println("bytes size:"+siz);
        Object res[] = new Object[siz];
        for (int i = 0; i < res.length; i++) {
            res[i] = new byte[80];
        }
        System.out.println("bytes " + benchFullGC());
        if ( res.equals("no") )
            System.out.println("bla");
    }

    private static void benchArr(int siz) {
        System.out.println("arr size:"+siz);
        Object res = allocateWRPArr(siz);
        System.out.println("small "+benchFullGC());
        res = allocateWRPArrMiddle(siz);
        System.out.println("middle " + benchFullGC());
        res = allocateWRPArrLarge(siz);
        System.out.println("large " + benchFullGC());
        if ( res.equals("no") )
            System.out.println("bla");
    }

    public static void main( String a[] ) {
        benchLinked(100000);
        benchArr(100000);
        benchBytee(100000);

        benchLinked(100000*3);
        benchArr(100000 * 3);
        benchBytee(100000 * 3);

        benchLinked(100000*6);
        benchArr(100000 * 6);
        benchBytee(100000 * 6);

        benchLinked(100000*10);
        benchArr(100000 * 10);
        benchBytee(100000 * 10);

        benchLinked(100000*10*10);
        benchArr(100000 * 10 * 10);
        benchBytee(100000 * 10 * 10);
    }

}
