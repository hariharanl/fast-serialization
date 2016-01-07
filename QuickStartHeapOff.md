<p><i>FST - Fast Serialization</i></p>

---


**What is Heap-Offloading ?**

Usually all non-temporary objects you allocate are managed by java's garbage collector. Although the VM does a decent job doing garbage collection, at a certain point the VM has to do a so called 'Full GC'. A full GC involves scanning the complete allocated Heap, which means GC pauses/slowdowns are proportional to an applications heap size. So don't trust any person telling you 'Memory is Cheap'. In java memory consumption hurts performance. The G1 Collector of 1.7 may improve the situation, but anyway .. the hurt of huge heaps will persist.

One **solution** to these memory requirements, is to 'offload' parts of the objects to the non-java heap (directly allocated from the OS).
Fortunately java.nio provides classes to directly allocate/read and write 'unmanaged' chunks of memory (even memory mapped files).

So one can allocate large amounts of 'unmanaged' memory and use this to save objects there. In order to save arbitrary objects into unmanaged memory, the most viable solution is the use of Serialization.
This means the application serializes objects into the offheap memory, later on the object can be read using deserialization.

The heap size managed by the java VM can be kept small, so GC pauses are in the millis, everybody is happy, job done.

It is clear, that **the performance of such an off heap buffer depends mostly on the performance of the serialization implementation**. Good news: for some reason FST-serialization is pretty fast :-).

FST encapsulates the low level stuff by providing 2 classes:

  * `FSTOffheap` - a simple off heap collection. One can add objects and search the collection using an iterator. Additionally each object can be saved with a tag object to speed up linear search later on.
  * `FSTOffHeapMap` - an implementation of Map, which puts its value Objects to directly allocated 'native' memory.


Sample usage scenarios:
  * Session cache in a server application. Use a memory mapped file to store gigabytes of (inactive) user sessions. Once the user logs into your application, you can quickly access user-related data without having to deal with a database.
  * Caching of computational results (queries, html pages, ..) (only applicable if computation is slower than deserializing the result object ofc).
  * very simple and fast persistance using memory mapped files


<br><br>
<b>What performance to expect ?</b>

For benchmarking I use a simple 'Order' class. It is clear performance of Offheap depends on the complexity of the objects stored.<br>
<br>
benchmark reading/writing 4 millions of those objects (FST v1.12) is<br>
<a href='http://fast-serialization.googlecode.com/files/offheap.html'>here</a>

As one can see, performance of off-heap storage is far slower than Java-Heap storage. However it is much faster than using other methods to move  big data outside the VM (Databases etc.). It is clear that an applications needs some kind of indexing in order to access the data, straight forward search will not be fast enough in most cases.<br>
However accessing (and deserializing) a stored Object <b>within 1.1 microseconds</b> (~800 per ms) is decent and much faster than any kind of other storage methods except the built in java heap.<br>
<br>
<br><br>
<b>FST Offheap classes</b>

<i>all objects added off-heap, must be serializable. FST annotations are acknowledged.</i>

<b><code>FSTOffHeap</code></b>

Basic Offheap store. It allocates a chunk of memory and allows to add Objects to it. Objects cannot be removed.<br>
In order to allow searching/iterating, one can associate a "tag" object with each entry (e.g. <code>offheap.add(person, person.getName()</code> ). When iterating tag-wise, only the tag is deserialized, so it is possible to search quicker only deserializing the tag object (note its an extended subclass of iterator). The heap store does not grow automatically.<br>
Each add() returns an int handle which can be used to obtain the stored object later on.<br>
<br>
By initializing <code>FSTOffHeap</code> with a memory mapped file buffer, one may use this class to write to files.<br>
<br>
<i>multi threading</i>

Use the getAccess() method to get thread safe concurrent access to the offheap. Iteration is thread safe also.<br>
<br>
This is only a basic building block for higher level functionality (e.g. offheap queue and offheap map), however it can be useful to save heap memory, by off-heaping e.g. tons of strings or other frequently used objects of your application.<br>
<br>
<br>
<br>
<b><code>FSTOffHeapMap</code> -UNDER CONSTRUCTION-</b>

A Hashmap implentation which stores its value objects offheap. Note that you need to re-organize the map from time to time as the underlying offheap grows with each object added (aka remove does remove a key from the map, not the underlying buffer). The easiest way to reorganize is to create a new offheap map and put all values from the old to the new map.<br>
This class is not thread safe.<br>
<br>
<b><code>FSTOffHeapQueue</code> -UNDER CONSTRUCTION-</b>

A 'binary Queue' implementation. Objects are serialized directly when they are added to the queue, so queued objects do not consume java heap space. One can take Objects or plain byte arrays representing a serialized objects from the queue. Vice versa it is possible to add byte arrays representing a serialized object and take a deserialized object.<br>
<br>
This is very useful for distributed applications. Typically producer threads put objects to the queue, a sender threads takes byte arrays and  send them over the net, the receiver process then adds the raw byte array to his receiving queue instance and reads deserialized objects from his receiving queue.<br>
This reduces the danger of running OutOfMemory under high load conditions, where large queues occur frequently. There is no performance impact, as one has to serialize the objects anyway in order to send them over the net.<br>
<br>
<i>multithreading</i>

<code>FSTOffHeapQueue</code> supports two usage patterns:<br>
<ul><li>many readers/many writers<br>
</li><li>single reader/writer with <b>multi-threaded decoding</b></li></ul>

multi threaded en/decoding can boost the speed of your application in case your process receives/sends one large stream of events to only a few receiver/sender instances. In this scenario throughput can be greatly improved by using multi threaded en/decoding (>400%). This lets you make use of multi core servers even if your application is basically single threaded. Multi threaded en/decoding is done behind the scenes and does not mess up the message order in the queue.<br>
<br>
In case of client/server applications (one sender/many receivers) the amount of concurrency usually is high enough to get something from your 16 core server, in these scenarios you just access the queue concurrently.<br>
For details see OffHeapQueue