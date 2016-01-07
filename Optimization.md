<p><i>FST - Fast Serialization</i></p>

---

## Optimization ##

see also my blog post:
http://java-is-the-new-c.blogspot.de/2013/10/still-using-externalizable-to-get.html

FST defines a set of annotations which influence the en/decoding of objects at runtime. Note that default FST (no annotations) is optimized the most. So prepare for a tradeoff speed <=> serialization size. However frequently it is more important to minimize the size of an object because of bandwith considerations, further processing (remoting in a cluster) etc.

FST has some utility methods which let you measure the effects of your optimization:

```
FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
// .. register some serializers etc.
YourObjectToSerialize ex = new YourObjectToSerialize(); // <= annotate this or not

System.out.println("Size:"+conf.calcObjectSizeBytesNotAUtility(ex));   
System.out.println("Write:"+conf.calcObjectWriteTimeNotAUtility(1000000, ex));
System.out.println("Read:"+conf.calcObjectReadTimeNotAUtility(1000000, ex));
```

Note that these methods are not intended for usage at runtime ever ! The times are given in microseconds else it would be hard to measure the duration of small objects. In case you test smallish Objects (e.g. 5..10 variables, no collections) consider creating a temporary array of 50 or so instances of your object, else the measuring will be skewed by the time required to initialize the objectstreams itself. However if you play around with `@EqualnessIsBinary` this might skew the Object size :).

**FSTConfiguration**

for tiny object graphs a big performance gain can be achieved by turning off tracking of identical objects inside the object graph (`FSTConfiguration.setShareReferences`). However this requires all serialized objects to be cycle free (most useful when serializing arguments e.g. marhalling RPC's).
A better approach is, to define this per-class using the @Flat annotation.

**`@Compress`**

The compress annotation lets FST do moderate compression on the annotated field.
It is currently aknowledged only for Strings and integer arrays.


For Strings an attempt is made to squeeze 2 chars into a byte .. this works good for plain text containing small caps. Don't use it for large caps-only Strings. For normal english strings expect a saving of 10..30%. Note that the String should at least have >12 chars to make this effective. The performance hit for this compression is moderate (far lower than a real compression algorithm). It is guaranteed that the string will not grow in size due to compression, you only have a small performance hit in string writing then (<10%).


For integer arrays a detection is done wether one of the following algorithms succeeds in reducing the size:

  * compact int - codes small numbers in 1..3 bytes, but large in 5
  * thin int array - successfull if you have a lot of '0' values in a int array
  * diff int - successful if your integer array contains little volatility (e.g. chart data). Only the first value is written, after that only the difference to the next value is written.
  * offset int - computes min an max of the int array. In case the range is < 65535, the min value and a series of short's is written.



**`@Conditional`**

see QuickStart page



**`@EqualnessIsBinary`**

This option may greatly reduce the size of the serialized Objects. In todays enterprise application one frequently has value type objects such as `Person { name, prename, age, id } ` or `ValuePair { int key, String value } `. Frequently these objects result from some generated data source  abstraction layer such as Hibernate or some homegrown generated stuff.
Unfortunately often you get several instances for the same business object. Example you have 2 Person objects which are equal in all their field values.

FST can detect those double instances during serialization and manages to write only one instance. At deserialization time, the `InputStream ` is "rewinded" and the single copy is read several times, so at read time you get an exact copy of the object graph you serialized.

Since this consumes some CPU-time, detection is performed only for classes tagged with `EqualnessIsBinary`. Note that you have to implement equals and hashCode correctly to make this work. Also note if the equals method does not cover all fields of a class, you may get wrong results upon deserializing. last but not least, there must be a significant amount of 'double' objects in your object graph, else you just waste CPU.

Example:
```
@EqualnessIsBinary
public class ObjectOrientedDataType implements Serializable {

    // encapsulate like a boss
    @Compress
    private String aString="";
    private boolean isCapableOfDoingAnythingMeaningful;
    public ObjectOrientedDataType() {}

    public ObjectOrientedDataType(String valueString) {
        if ( valueString == null ) {
            isCapableOfDoingAnythingMeaningful = false;
            aString = "";
        } else {
            this.aString = valueString;
        }
    }

    public boolean equals( Object o ) {
        if ( o instanceof ObjectOrientedDataType) {
            ObjectOrientedDataType dt = (ObjectOrientedDataType) o;
            return dt.aString.equals(aString) 
                && dt.isCapableOfDoingAnythingMeaningful == isCapableOfDoingAnythingMeaningful;
        }
        return super.equals(o);
    }

    public int hashCode() {
        return aString.hashCode();
    }

    public String toString() {
        return aString;
    }
}
```


**`@EqualnessIsIdentity`**

same as `@EqualnessIsBinary`, but does not copy the Object at deserialization time, but simple reuses the same instance. This does make sense only if the receiver/reader cannot or will not modify this object, else you might get weird effects ...

FST applies this optimization automatically for Strings. Note that a reference still costs 3 to 6 bytes, so if an object only has one field such as Date, it does not pay off (applies also to `@EqualnessIsBinary`).


**`@Flat`**

This is a pure CPU-saving annotation. If a field referencing some Object is marked as @Flat, FST will not do any effort to reuse this Object. E.g. if a second reference to this Object exists, it will not be restored but contain a copy after deserialization. However if you know in advance there are no identical objects in the object graph, you can save some CPU time. The effects are pretty little, but in case you want to save any nanosecond possible consider this ;). If a class is marked as `@Flat`, no instance of this class will be checked for equalness or identity. Using this annotation can be important when you serialize small objects as typical in remoting or heap offloading.


**`@Plain`**

by default int arrays are saved in a compact form. Actually most integers do not use full 32bit. However worst case (lots of large int numbers), this will increase the size of an int array by 20%. In case you know this in advance you may switch to plain 4-byte per integer encoding using this annotation on an int array. Useful for image or sound data.

**`@Predict`**

Can be used at class or field level. At class level, the classes contained in the Predict array, are added to the list of known classes, so no full classnames for those classes are written to the serialization stream.
If used at field level, it can save some bytes in case of losse typing. E.g. you have a reference typed 'Entity' and you know it is most likely a 'Employee' or 'Employer', you might add `@Predict({Employ.class,Employer.class})` to that field. This way only one byte is used to denote the class of the Object. This also works for fields containing losely typed Collections.

Since FST does a pretty decent job minimizing the size of class names, this optimization makes sense only if you write very short objects, where the transmission of a classname might require more space than the object itself.

Note it is possible to directly register Classes in advance at the FSTConfiguration object. This way the class name is never transmitted. Ofc this must be done at writing and reading side.


**`@Thin`**

applicable to int and object arrays, enforces 'Thin' compression as described above in `@Compress` section.

**`@OneOf`**

applicable to String references. Often Strings contain de-facto enumerations, but are not declared so. To prevent the transmission of constant Strings, one can define up to 254 constants at a field reference. If at serialization time the field contains one of the constants, only one byte is written instead of the full string. If the field contains a different string, this one is transmitted. So the list can be incomplete (e.g. just denote frequent values or default values).

```
...
@OneOf({"EUR", "DOLLAR", "YEN"})
String currency;
...
@OneOf({"BB-", "CC-"})
Object euRating;
...
```