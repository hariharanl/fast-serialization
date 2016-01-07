<p><i>FST - Fast Serialization</i></p>

---

## Short Guide ##

(Maven see project page.)



Content:

[Simple ObjectOutputStream Replacement](#Plain_ObjectOutputStream_Replacement.md)

[Recommended Use of FST Streams](#Recommended_threadsafe_Use.md)

[FSTConfiguration](#What_is_that_FSTConfiguration_?.md)

[Conditional Decoding](#Conditional_Decoding.md)

[Custom Serializers](#Custom_Serializers.md)

[About Unsafe and ByteOrder Issues](#Unsafe_/_Byte_Order_Issues.md)


[Complete Source Example](http://code.google.com/p/fast-serialization/source/browse/trunk/src/test/java/de/ruedigermoeller/serialization/testclasses/docusample/FSTTestApp.java)

#### Plain `ObjectOutputStream` Replacement ####

Basically you just replace the `ObjectOutputStream, ObjectInputStream` with `FSTObjectOutput,FSTObjectInput`.
```
public MyClass myreadMethod( InputStream stream ) throws IOException, ClassNotFoundException 
{
    FSTObjectInput in = new FSTObjectInput(stream);
    MyClass result = (MyClass)in.readObject();
    in.close(); // required !
    return result;
}

public void mywriteMethod( OutputStream stream, MyClass toWrite ) throws IOException 
{
    FSTObjectOutput out = new FSTObjectOutput(stream);
    out.writeObject( toWrite );
    out.close(); // required !
}
```

if you know the type of the Object (saves some bytes for the class name of the initial Object) you can do:
```
public MyClass myreadMethod(InputStream stream) throws IOException, ClassNotFoundException
{
    FSTObjectInput in = new FSTObjectInput(stream);
    MyClass result = in.readObject(MyClass.class);
    in.close();
    return result;
}

public void mywriteMethod( OutputStream stream, MyClass toWrite ) throws IOException 
{
    FSTObjectOutput out = new FSTObjectOutput(stream);
    out.writeObject( toWrite, MyClass.class );
    out.close();
}
```
**!** Note that if you write with a type, you also have to read with the same type.

**!** Note that if you create an instance with each serialization you **should close** the FSTStream, because behind the scenes some datastructures are cached and reused. If this fails, you might observe a a performance hit (too much object creation going on), especially if you encode lots of smallish objects.



#### Recommended threadsafe Use ####

In order to optimize object reuse and thread safety, FSTConfiguration provides 2 simple factory methods to obtain input/outputstream instances:

```
...
// ! reuse this Object, it caches metadata. Performance degrades massively
// if you create a new Configuration Object with each serialization !
static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
...
public MyClass myreadMethod(InputStream stream) throws IOException, ClassNotFoundException
{
    FSTObjectInput in = conf.getObjectInput(stream);
    MyClass result = in.readObject(MyClass.class);
    // DON'T: in.close(); here prevents reuse and will result in an exception      
    stream.close();
    return result;
}

public void mywriteMethod( OutputStream stream, MyClass toWrite ) throws IOException 
{
    FSTObjectOutput out = conf.getObjectOutput(stream);
    out.writeObject( toWrite, MyClass.class );
    // DON'T out.close() when using factory method;
    out.flush();
    stream.close();
}
```

This will create and reuse a single `FSTIn/OutputStream` instance per thread, which implies you should not save references to streams returned from that method.

**Zero Privacy :)**

`FSTObjectOutput` temporary serializes into a byte array. You can set the underlying byte array, access it etc. . This makes it easy i.e. to serialize an Object, then take the resulting byte array and do whatever you want with that. Both streams have a "resetForReuse()" which allows to reuse a given instance (e.g. write an object, take the bytearray and send it somewhere or multiple times without re-encoding, reset and write another object). This way one can avoid massive object creation overhead often induced by closed Stream Interfaces. Ofc you have to be careful regarding accidental shared byte[.md](.md) arrays when doing so.

#### What is that `FSTConfiguration` ? ####

This class defines the encoders/decoders used during serialization. Usually you just create one global singleton (instantiation of this class is very expensive). Usage of several distinct Configurations is for special use cases which require some in-depth knowledge of FST code. You probably never will need more than this one default instance.

e.g.

```
public class MyApplication {
    static FSTConfiguration singletonConf = FSTConfiguration.createDefaultConfiguration();
    public static FSTConfiguration getInstance() {
        return singletonConf;
    }
}
```

You can customize the FSTConfiguration returned by `createDefaultConfiguration()`. E.g. register new or different serializers, some hooks, set additional flags on defined serializers etc. . Just have a look on the source.

One easy and important Optimization is to register classes which are serialized for sure in your application at the FSTCOnfiguration object. This way FST can avoid writing classnames.

**!** Reader and writer configuration should be identical. Even the order of class registration matters.



#### Multi threaded read/write ####

You cannot read write from different Threads into the same `FSTInput/OutputStream`. However you can create/use an arbitrary number of `FSTInput/OutputStreams` sharing one FSTConfiguration. Just see above "Recommended Usage"


#### Huge Objects / chunked,streaming I/O: ####

The encoded Objects are written to the underlying stream once you close/flush the FSTOutputStream. Vice versa, the FSTInput reads the full stream until it starts decoding.

This may be a problem in case you read/write huge Objects or want to stream an object graph in small chunks.

A work around in the current version would be to write your Objects chunked (e.g. if you have a List of Objects to serialize, create a new FSTObjectOutput for each Object of the list). Usually allocating some 100 kByte (or even MB) of byte arrays should not be a problem, I just mention this limitation in case you plan reading/writing huge Object Trees or you are trying to stream an object graph in small chunks.


#### Conditional Decoding ####

There are scenarios (e.g. when using multicast), where a receiver conditionally wants to skip decoding parts of a received Object in order to save CPU time. With FST one can achieve that using the @Conditional annotation.

```
class ConditionalExample {
   int messagetype;

   @Conditional
   BigObject aBigObject;

...
}
```

if you read the Object, do the following:
```
        FSTObjectInput.ConditionalCallback conditionalCallback = new FSTObjectInput.ConditionalCallback() {
            @Override
            public boolean shouldSkip(Object halfDecoded, int streamPosition, Field field) {
                return ((ConditionalExample)halfDecoded).messagetype != 13;
            }
        };
        ...
        ...
        FSTObjectInput fstin = new FSTObjectInput(instream, conf);
        fstin .setConditionalCallback(conditionalCallback);
        Object res = in.readObject(cl);


```

The `FSTObjectInput` will deserialize all fields of `ConditionalExample` then call 'shouldSkip' giving in the partially-deserialized Object. If the shouldSkip method returns false, the @Conditional reference will be decoded and set, else it will be skipped.

#### Custom Serializers ####

By default FST falls back to the methods defined by the JDK. Especially if private methods like 'writeObject' are involved, performance suffers, because reflection must be used. Additionally the efficiency of some stock JDK classes is _cruel_ regarding size and speed. The FST default configuration (`FSTConfiguration.createDefaultConfiguration()`) already registers some serializers for common classes (popular Collections and some other frequently used classes).
So if you have trouble with stock JDK serilaization speed/efficiency, you might want to register a piece of custom code defining how to read and write an object of a specific class.

the basic interface to define the serialization of an Object is `FSTObjectSerializer`. However in most cases you'll use a subclass of `FSTBasicObjectSerializer`.

The FSTDateSerializer delivered with FST (note the registration in the instantiate method, you need to do it if you instantiate the object by yourself):
```
public class FSTDateSerializer extends FSTBasicObjectSerializer {
    @Override
    public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) 
    {
        out.writeFLong(((Date)toWrite).getTime());
    }

    @Override
    public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy)
    {
    }

    @Override
    public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPositioin) 
    {
        Object res = new Date(in.readFLong());
        in.registerObject(res,streamPositioin,serializationInfo);
        return res;
    }
}
```

a serializer is registered at the `FSTConfiguration` Object:
```
   ...
   conf = FSTConfiguration.createDefaultConfiguration();     
   conf.registerSerializer(Date.class, new FSTDateSerializer(), false);
   ...
```

(ofc you have to use exactly this configuration later on in the `FSTObjectIn/OutputStream`).

The point of having 3 methods (read, write, intantiate) allows to read from the sream **before** creating the object (e.g. to decide which class to create). Common case is to just override and implement read/write, however there are cases where read is empty and the full object is created and read in the instantiate method.

#### Unsafe / Byte Order Issues ####

Since version 1.23 FST makes use of unsafe operations to read/write Object fields. It has been proven to be reliable across machines, JDK version and operating systems (WinX,Linux) in production systems.

If `FSTConfiguration.preferSpeed` is true, also native arrays will be serialized using unsafe operations, which means no value compression is applied and may result in significant higher size of a serialized object. However often speed matters more than size e.g. when serializing to Off-Heap, Shared Memory queues or fast networks such as IB or 10GBit ethernet (even 1GBit ethernet is not that easy saturated if one uses some of the various slowish enterprise frameworks).

Usage of Unsafe can be disabled by calling `"System.setProperty("fst.unsafe","false")"` **prior to referencing any FST class**. A better approach is to switch Unsafe usage at command line like `java -Dfst.unsafe=true ...` when starting your program.


If you use FST in client server applications or heterogenous networks you might run into byte order issues, as the byteorder of an x86 and (RIP) Solaris SPARC machine are different.

In contradiction to standard Java IO, FST always assumes x86 byte order even when Unsafe is turned off, this means you can encode from an x86 server with Unsafe enabled and decode on a Client with another processor architecture as long Unsafe on the Client is disabled.

So on Big Endian platforms, never turn on Unsafe usage wether its a client or server machine. Ofc this does not hold true if ALL machines de/encoding FST Objects are Big Endian.

**Clarification:** Disable unsafe on all Big Endian Platforms (non-x86), except when all machines (client+server) are Big Endian.
You can enable Unsafe always on Little Endian (x86) machines.