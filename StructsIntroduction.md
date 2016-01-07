<p>_FST - Structs </p>

---_

**Disclaimer:**

This library uses unsafe and direct memory access. There are some sanity checks, however you can easily construct (wrong) code which will let your VM coredump.
I migtht provide a ByteBuffer backed version later on, but currently the difference in performance inbetween a "safe" and an "unsafe" implementation is too big.
This is not for beginners and some C background might help. Ensure you understand the basic concepts before throwing this at your code.
Pls don't hesitate to file an Issue in case you detect problems, errors or missing sanity checks. This will help me getting this bugfree.

Doing low-level, unsafe Java coding is less errorprone and more portable than mixing in C/C++ via JNI. Additionally JITs have become very sophisticated, so low-level Java often performs better than a C/JNI mix in.

**You are encouraged to at least read every heading of this document, trial-and-error will not work that good for this library**

## Introduction ##

_In C++, one needs to sacrifice performance in order to get convenience, in Java one needs to sacrifice convenience in order to get performance_


While serialization can flatten any Object Graph into a byte array, one needs to deserialize the Object in order to access the data contained. Frequently (offheap objects, locality) one would like to access the data without the costly "deserialization"-part.

FSTStructs provide a way to store and access data in a deterministic structured layout in a continuous chunk of memory.
I therefore use runtime byte code generation and (may change in future) the Unsafe class in order to redirect member access inside methods to an underlying `byte[]`} array.

While there are a lot compromises to be made (a clean, orthogonal implementation would require VM extensions) when defining FSTStructs, there are a lot of benefits when "flattening" data structures in a still accessible way:

  * low GC cost (store GB of structured data with <1s Full GC duration)
  * (un-)marshalling is equal to a memory copy. This speeds up inter process communication using shared memory or network messages.
  * use memory mapped files to virtually enlarge your memory, as it is possible to control memory layout of your data
  * faster iteration of complex data structures compared to On-Heap due to control over in-memory layout (locality!) and "pointer-alike" memory access patterns
  * data structures can be de/encoded easy from external languages
  * nearby allocation free code for extreme requirements (e.g. latency).

see my blog for some research regarding effects of instance saving and locality on GC:

http://java-is-the-new-c.blogspot.de/2013/07/what-drives-full-gc-duration-its.html

http://java-is-the-new-c.blogspot.de/2013/07/impact-of-large-primitive-arrays-blobs.html


Memory layout:

https://fast-serialization.googlecode.com/files/structlayout.PNG

## How does this work ? ##

Structs are stored in byte arrays, additionally FST generates "pointer"-alike Wrapper Classes at runtime providing access to the flattened objects inside the byte array. One could put an arbitrary number of struct instances into a single byte array.

To define the actual layout of a struct, a program provides an OnHeap template instance. This template instance then can be used to create new struct instances with help of an Allocator instance. The values of the template instance also determine initial values of a struct instance. Constructors of a struct class are not executed (except one creates the struct class on-heap using regular "new").

Technically FST separates object header and instance fields. So the data of an object is stored inside a flat byte[.md](.md). In order to access the data, an accessor instance pointing to the embedded data has to be created. By moving the address-pointer of such an accessor class (generated at runtime) one can use a single instance to read different datasets stored in the flat byte array. FST has a per-thread cache of those wrappers (i call them pointer) in order to enable transparent access to structures and embedded substructures.

Code Examples:
```
        
        // build template
        TestInstrument template = new TestInstrument();
        template.legs = new TestInstrumentLeg[] { new TestInstrumentLeg(), null, null, null };
        // use template to allocate 'byte-packed' instances
        FSTStructAllocator<TestInstrument> allocator = 
             new FSTStructAllocator<TestInstrument>(template,SIZE);

        StructArray<TestInstrument> instruments = allocator.newArray(100000);

```

results in a flat array of in-place copies of the given "template" instance. One can access this like usual object structures like

```
        sum = 0;
        for ( int i = 0; i < instruments.size(); i++ ) {
            sum+=instruments.get(i).getAccumulatedQty();
        }
```

In order to pass a structure embedded object to outer code, a 'pointer' (accessor class instance) must be created. FST Byte code instrumentation automatically creates and caches those 'accessor classes'. However if you want to 'keep' (assign) such a struct object, a call to 'detach()' is required. Else subsequent calls will point your accessor to another instance (accessors are reused).


```
        TestInstrumentLeg toDetach = instruments.get(i).legs(1);
        toDetach.detach();
        otherCode(toDetach);
```

Embedded Objects can be rewritten, however one gets an exception if the new object requires more bytes than the previous.

some benchmarks:
https://fast-serialization.googlecode.com/files/structiter.html

## Rules for structable classes ##

  * a structable class has to inherit FSTStruct
  * no direct public field access possible. You need to use getter/setter methods. This does not apply for member methods.
  * a structable class can only contain fields that reference FSTStruct and subclasses or primitives. It is not possible to have references from within a struct to a heap object.
  * all fields and methods must be non-final public or protected. (required to enable instrumentation)
  * Valid structs cannot be inner classes of other FSTStruct classes.
  * no direct references to embedded arrays are allowed. You have to create array accessor methods following a defined naming pattern.
  * no support for arrays of arrays (multidimensional) within structs
  * all sizings are determined at instantiation time, this also applies to strings. `StructStrings` are fixed size.
  * there are several method naming schemes which are recognized by FST byte code instrumentation. This way it is possible to e.g. get the base index of an embedded int array in case.
  * if you want to 'keep' a reference to an embedded object (store tmp variable in a loop or field) you need to call detach() on that reference
  * you cannot synchronize on embedded objects
  * the template object defining the layout and initial values of struct(s) must not contain cyclic references
  * System.identityHashcode delivers the identity of the access wrapper, not the embedded object.

## Simple fields ##

primitive fields can be declared straight forward and can be accessed directly from method code.

```
public class SubStruct extends FSTStruct {
    StructString name = new StructString(30);
    int age = 42;
    ... getters / setters ommitted here ...
}

public class Example extends FSTStruct {

    protected int intField;
    protected double d = 66666666666.66;
    protected SubStruct sub = new SubStruct();

    public int getIntField() { return intField; }
    public void setIntField(int val) { intField = val; }

    public SubStruct getSub() { return sub ; }
    public void setSub(SubStruct val) { sub = val; }

    public void multiply(int mul) {
        // direct member access is OK inside your struct class (and subclasses)
        intField *= mul; 
    }

    public double getD() {
        return d;
    }

    public String toString() {
        return "Example if:"+intField+" d:"+d;
    }
}
```

Note, that this class is fully operable when allocated as usual on the heap.

if we create a struct array from that

```
    STStructAllocator<Example> allocator = 
             new FSTStructAllocator<Example>(new Example());

    StructArray<Example> exampleArray = allocator.newArray(1000000);

    Example onHeap = new Example();
    exampleArray.get(10).getIntField(); // get volatile pointer to 10't element and get intVal
    exampleArray.get(10).getSub().getName().setString("Me"); // rewrite StructString content
    exampleArray.get(10).getSub().setName( new StructString("You") ); // rewrite StructString object

```

we get an array of 1 million Example struct instances, initialzed as a copy of the template given by "new Example()". The difference is, that a "normal" implementation would have created 3.000.001 Objects (array and 1 million Example, 1 million substruct, 1 million structstring instances), which (if not temporary) will cost the garbage collector ~800 to 1250 ms to traverse.
The struct example actually allocates one large byte array, which will have practical no impact on GC duration. The data is 'hidden' from the Garbage Collector.

**Important:** calling a setter on an struct's embedded object will **copy** the given object. When calling a setter on normal object, a reference will be stored. For structs, every set is "by value" not by reference. This is a technical necessity.

if you examine in a debugger the (volatile) structpointer obtained by `exampleArray.get(10).getSub()` you will note, that the instance variables are null or 0. This is because FST instrumentation patches all accesses of the methods of 'Example' and redirects the read/write to the underlying byte array. This is not the case if you allocate it regular on the heap with `new`.

**Important:** If you need a permanent reference, call 'detach()' on the volatile access wrapper:
```
    SubStruct sub = exampleArray.get(10).getSub();
    sub.detach();
```

## Arrays of Primitives ##

while it is possible to patch field access in a methods byte code in order to redirect the code from fields to the `byte[]` backing the struct, this does not work for arrays. Therefore there are harder rules to follow when using arrays of primitives inside a struct's code.

```
class .. extends FSTStruct {
    protected int array[] = new int[50];
    public int array(int index) { return anArray[index]; }
    public void array(int index, int value) { anArray[index] = value; }
    public int arrayLen() { return anArray.length; }

    public void addToAll( int toAdd ) {
        for ( int i = 0; i < arrayLen(); i++ )
            array(i,array(i)+toAdd);
    }
```

All array accesses must use the 3 accessor methods. Once this class is struct-allocated, instrumentation will redirect those accessormethods to another place (array will be null then).

Again you can test your code using usual 'new' allocation as its hard to debug the instrumented struct version of your class.

The naming pattern has to be

```
protected|public int [arrayfieldName]Len()
protected|public int [arrayfieldName](int index)
protected|public void [arrayfieldName](int index, int value)
```

if these naming pattern is not used, instrumentation will not patch the method resulting in malfunction of your class once it is struct allocated.

With normal onheap allocation, the array will be allocated somewhere on the heap and the reference to this array is stored in the 'array' field of the example given above.

When this class is struct allocated, the array elements will directly sit behind the objects field data. This can actually increase access performance, as the risk of getting a CPU cache miss is much lower. A cache miss requiring the CPU to access main memory can be aequivalent to 300-1000 CPU instructions.

It is possible to obtain the base adress (explained below in hacking section) of the embedded array data and read this direct without the `[]` operator. This can actually be faster than usual array[index](index.md) on heap performance. However this is required in rare cases and the resulting code is very C-ish and strange.

## Arrays of Objects ##

arrays of substructures should be kept of equal types, else things might get complicated. The same rules as for primitive arrays apply, however there is the exception that you somehow try to set a larger Object than initially given by the template, this will result in an exception.

```
class .. extends FSTStruct {

    protected StructString array[] = { new StructString("x"), new StructString("xx"), new StructString("xxx",10)};

    public StructString array(int index) { return anArray[index]; }
    public void array(int index, StructString value) { anArray[index] = value; }
    public int arrayLen() { return anArray.length; }

```

Note that the array content has to be initialized in the template instance given to the allocator class. FST will search for the largest element in your template array, this will then determine the size of **each** element  in the struct array. In the example above, all StructStrings of the array will get a max len of 10, because the last element has this size.

One can also use the @Templated annotation to define the initial values of a Struct array.

```
class .. extends FSTStruct {

    @Templated
    protected StructString array[] = { new StructString("empty",120), null, null,};

    public StructString array(int index) { return anArray[index]; }
    public void array(int index, StructString value) { anArray[index] = value; }
    public int arrayLen() { return anArray.length; }

```

in this case all elements in the array will be initialized with a copy of the first element.

To define larger array size consider a template setup method like:

```
class MyStruct extends FSTStruct {

    public static MyStruct createTemplate() {
        MyStruct res = new MyStruct();
        // set template in first entry (see @Templated below)
        res.array[0] = new StructString("initial value");
        return res;
    }

    @Templated
    protected StructString array[] = new StructString[200];

    public StructString array(int index) { return anArray[index]; }
    public void array(int index, StructString value) { anArray[index] = value; }
    public int arrayLen() { return anArray.length; }

```

**untyped Object arrays**

it is possible to store losely typed substructures e.g.:

```
class UnTyped extends FSTStruct {

    protected FSTStruct array[] = { new StructString("x"), new StructInt(13), new SubStruct()};

    public FSTStruct array(int index) { return anArray[index]; }
    public void array(int index, FSTStruct value) { anArray[index] = value; }
    public int arrayLen() { return anArray.length; }

```

however things get tricky, you have to call cast() then when accessing those elements

```
for (..) {
   FSTStruct tmp = unTyped.array(i).cast();
   if ( tmp instanceof .. ) {
   }
}
```

a note on performance: if you set an object (field or array element), the content of the given object is copied in place of the previous object. Its faster, if you create an offheap instance of the object to set ((memCopy). Additionally you need fewer object allocations.
```
   StructString tmp = allocator.newStruct(new StructString(20));
   for ( int i = 0; .. ) {
      tmp.setString( "count "+i );
      mystruct.array(i,tmp); // copy !
   }
```
is faster than
```
   for ( int i = 0; .. ) {
      mystruct.array(i,new StructString("count "+i));
   }
```

## Built in Struct Types ##

**`StructString`** is a mutable String which can be used inside FSTStructs. One could add a lot more methods to this for convenience, however i was too lazy to do this, contributions are welcome.

**`StructArray`** is a fixed-size `ArrayList` alike. Mostly used as top level to allocate arrays of structs.

**`StructMap`** is a fixed size open addressed Hashmap implementation (no remove operation) operating completely inside the off heap. It does not like polymorphic key/values so keep key class and value class constant.
Can be used to flatten hashmaps. Additionally this is a proof of concept as there is actually some code operating on the structs underlying byte array.
It is slightly slower than `HashMap` when doing micro benchmarks, however in a real application it performs way better as locality (CPU cache) is maintained, while a `HashMap`'s keys and values are likely cluttered all over the heap (cache misses).

**`StructInt`**
present to be able to use integer keys in `StructMap`

## FSTStructAllocator and allocation patterns ##

Let's look on a object on the heap

https://fast-serialization.googlecode.com/files/comlexheapobject.PNG

Usually an object contains several subobjects. E.g. a String which in turn contains a character array etc. .
This can be transformed to a FSTStruct backed by a single byte array and an accesswrapper which 'acts' like the objects (with slightly different semantics, see section 'volatile access wrapper').

https://fast-serialization.googlecode.com/files/singlebyte.PNG

One can choose to create an access wrapper ('pointer) for each struct allocated, this will save instances in case the flattened Object has at least 2 subobjects (e. one String+its char[.md](.md)). With this use case one can treat the "complex Object" like any other object, synchronize on it, put it to on-heap collections etc. . The underlying byte array then will be freed when the wrapper is GC'ed.


Another option is to allocate larger chunks of byte[.md](.md) and store several structs inside this. FSTStructAllocator allows to set the chunksize measured in number of Objects or absolute bytes (may create unused bytes at the end of each chunk).

https://fast-serialization.googlecode.com/files/chunked.PNG

the byte array will be freed if all access wrapper objects pointing to it are freed.

**Note:** while the top-level access wrapper object feels like a normal object, references to its substructures are volatile.
```

   ComplexObject myobject = allocator.newStruct();
   normalHashMap.put( myobject, "this is ok");

   javautilHashMap.put( myobject.getString(), "WRONG !!! volatile reference stored");

   //right:
   StructString tmp = myobject.getString();
   tmp.detach();
   javautilHashMap.put( myobject.getString(), "now that's better");

   //better (create new string):
   javautilHashMap.put( myobject.getString().toString(), "no reference to byte[] stored");

```

most common case is probably create arrays with only one access object:

https://fast-serialization.googlecode.com/files/array.PNG

## Warning regarding persistence, IPC, messaging of FSTStructs ##

For each struct class an Id is assigned at runtime in the order structs are accessed. If you want to send a Struct to a remote VM or store them to disk, you need to manually define the id mapping, else process A might think `1 = MyStruct.class` while process 2 thinks `1 = MyVeryOtherStruct.class`.

You can define the id mapping by adding something like

`FSTStructFactory.getInstance().registerClz(MyStruct.class, MyOtherStruct.class, MySubStruct.class,... )`

**before** doing any struct stuff.

ALL structs must be registered for all processes in the exact same order, else you might be hit by access violations which is probably a new experience when programming java ;-) .

## "Volatile" accessors ##

As explained above, a struct consists of a "pointer" to the top level struct objects. Once subelements like arrays or embedded substrucures are accessed, additional access object need to be created for those substructures. As new creation would bog down access performance a lot, FST caches an access wrapper object for each struct class **per thread**.

```
   ComplexObject myobject = allocator.newStruct();

   StructString tmp1 = myobject.getString();
   StructString tmp2 = myobject.getOtherString();

   // tmp1 == tmp2 !!!!!!!
```

in the example above, actually the same object is returned, but the hidden `___offset` variable points to another position in the underlying byte array.
`tmp1.detach()` actually the pointer instance from the thread local cache, so one can obtain a permanent reference to a struct or part of it. Note each 'detach' call has a cost of a obejct creation.

```
   ComplexObject myobject = allocator.newStruct();

   StructString tmp1 = myobject.getString();
   tmp1.detach();
   StructString tmp2 = myobject.getOtherString();

   // tmp1 != tmp2
```


In short: whenever you want to keep a reference on an embedded struct, call detach on it.


## Performance ##

By using C-ish pointer moving, performance is often superior to usual on-heap structures. If one uses the convenience layer, performance of data access degrades with the nesting depth of substructures. Primitive fields on first level e.g. `mystruct.getInt()` are as fast as normal object field access after the JIT kicked in. `mystruct.getSubstructure().getInt()` is 2 to 3 times slower.

Benchmark iterating arrays of structs vs. arrays of on heap objects.

http://fast-serialization.googlecode.com/files/structiter.html

Details Pending ..

## Hacking, Special methods ##

Depending on naming pattern, instrumentation can provide low level information such as the start of an emebedded int array inside the underlying byte array etc ..

```
  ..
  int[] array;

  // returns the index of the first integer in the byte array obtained by
  // struct.getBase()
  public int arrayIndex() {
      return -1; // will be generated by instrumentation
  } 

  StructString objectArr[];
  // returns the size of a slot in an embedded Object array
  public int objectArrElementSize() {
      return -1; // will be generated by instrumentation
  } 
  
  // return a volatile pointer pointing to the zero elemenr of an embedded pointer
  // use FSTStruct.cast() to get a typed pointer
  public FSTStruct objectArrPointer() {
      return null; // will be generated by instrumentation
  } 
  
  // same as above, but move existng pointer to begin of array (object reuse)
  public void objectArrPointer(FSTStruct pointer) {
      // will be generated by instrumentation
  } 

  int anInt;
  
  // return the index in the underlying byte array of a struct's field.
  // for arrays and emebedded objects, this points to the header of the array
  // or object.
  // for primitive fields this points directly to the location of the data
  public int anIntStructIndex() {
      return -1; // will be generated by instrumentation
  } 

  // CAS assignment (supported for int and long only)
  public void anIntCAS( int expected, int value ) {
      // will be generated by instrumentation
  }

```


## Code examples ##

Struct class example:

Example of fast packet processing without any deserialization and copying required

(`DataPacket` is a FST-Struct http://code.google.com/p/fast-cast/source/browse/trunk/src/de/ruedigermoeller/fastcast/packeting/DataPacket.java

```
private void receiveDatagram(DatagramPacket p) throws IOException {
        if ( trans.receive(p) ) {
            receivedPacket.baseOn(p.getData(), receivedPacket.bufoff + p.getOffset(), receivedPacket.___fac);

            boolean sameCluster = receivedPacket.getCluster().equals(clusterName);
            boolean selfSent = receivedPacket.getSender().equals(nodeId);

            if ( sameCluster && ! selfSent) {

                int topic = receivedPacket.getTopic();
                if ( topic > MAX_SCOPE || topic < 0 ) {
                    FCLog.get().warn("foreign traffic");
                    return;
                }
                if ( receiver[topic] == null && sender[topic] == null) {
                    return;
                }

                Class type = receivedPacket.getPointedClass();
                StructString receivedPacketReceiver = receivedPacket.getReceiver();
                if ( type == DataPacket.class ) {
                    if ( receiver[topic] == null )
                        return;
                    if (
                        ( receivedPacketReceiver == null || receivedPacketReceiver.getLen() == 0 ) ||
                        ( receivedPacketReceiver.equals(nodeId) )
                       )
                    {
                        dispatchDataPacket(receivedPacket, topic);
                    }
                } else if ( type == RetransPacket.class ) {
                    if ( sender[topic] == null )
                        return;
                    if ( receivedPacketReceiver.equals(nodeId) ) {
                        dispatchRetransmissionRequest(receivedPacket, topic);
                    }
                } else if (type == ControlPacket.class ) {
                    ControlPacket control = (ControlPacket) receivedPacket.cast();
                    if ( control.getType() == ControlPacket.DROPPED ) {
                        receiver[topic].getTopicEntry().getService().droppedFromReceiving();
                        receiver[topic] = null;
                    }
                }
            }
        }
    }
```

http://code.google.com/p/fast-serialization/source/browse/trunk/src/test/java/de/ruedigermoeller/heapofftest/structs/TestData.java

Struct test:

http://code.google.com/p/fast-serialization/source/browse/trunk/src/test/java/de/ruedigermoeller/heapofftest/structs/StructTest.java

more complex struct structure classes examples:

http://code.google.com/p/fast-serialization/source/browse/#svn%2Ftrunk%2Fsrc%2Ftest%2Fjava%2Fde%2Fruedigermoeller%2Fheapofftest%2Fstructs%253Fstate%253Dclosed

benchmark accessing structs:

http://code.google.com/p/fast-serialization/source/browse/trunk/src/test/java/de/ruedigermoeller/heapofftest/structs/BenchStructIter.java

another freestyle bench (uses internal classes, no cut and paste stuff)

http://code.google.com/p/fast-serialization/source/browse/trunk/src/test/java/de/ruedigermoeller/heapofftest/BenchStructs.java