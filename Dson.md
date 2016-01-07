**This is beta, don't use yet**

Dson is an extension of the popular JSON format. The FST Dson classes allow for easy and elegant mapping of text to object graphs.
It can be used
  * for language agnostic communication/data exchange
  * have an easy way to provide and parse config files
  * to create low-tec and easy to manage DSL's

Currently Java and Dart is supported. Since the encoding is simple, it can be ported pretty quick (within some hours).

Its called 'D'son because it derives from Json and .. erm you know Dart starts with 'D' ..

**Why yet another text based structured data flavour ?**

JSON was born out of the `JavaScript` syntax. A major issue in using it to serialize arbitrary Java object graphs is the absence of type information/tagging. Frequently a special attribute is used like

```
 {
   "@type":"de.wombat.Wombat",
   "attribute":"value",
   ..
 }
```

Since order of attributes is not guaranteed, a parser has to use an intermediate Map structure when reading, because the type of an object is not known prior to parsing. If used as a network protocol this can impose significant overhead.

Yaml has a similar problem, but it can be solved using "!de.wombat.Wombat" style annotations. Unfortunately Yaml has a pretty large spec, so there are only few (very slow) implementations of this format.

**Dson**

differs from JSON in only a few aspects, so still keeps the minimalism for easy implementation.

  * the key attribute name of an object structure has no double quotes.
  * each object gets a name denoting the type of the object (not necessarily full qualified class names!)
  * instead of ',' whitespace can be used as separator (optional).
  * it adds comments

All literal rules and escape sequences are identical to JSON.

Dson example:

```
ugly.full.qualified.ClassName {
  
  user:"Me"
  pwd:"usual"
  array: [ 1,2,3,4 ]

  nestedObject: other.ugly.full.qualified.ClassName { name: 'aName' age: 13 email: "Not@me.com" }
}
```

in order to eliminate full qualified classnames, an application can provide a mapping String<=>full qualified ClassName, so it looks like this:

```
authreq {
  user:"Me"
  pwd:"usual"
  array: [ 1,2,3,4 ]

  nestedObject: userdata { name: 'aName' age: 13 email: "Not@me.com" }
}
```

now that's curly .. optionally one can ommit `'{}'` but signal the end of an object structure with `';'`.

```
authreq
  user:  "Me"
  pwd:   "usual"
  array: [ 1,2,3,4 ]
  nestedObject: 
    userdata name: 'aName' age: 13 email: "Not@me.com" ;
;
```

[_oops .. looks like Smalltalk code .. one more year and FST will be the first serialization lib including a full fledged Smalltalk interpreter ;-)_]

This is a little change to JSON, but it enables direct mapping of Java object graphs to text and vice versa. Data can be read into the associated java classes without temporary structures, which should give a major speed advantage. The Dson implementation of FST does not implement all optimizations possible, however Strings and short/int/long values are directly decoded "into" an object's field. Since these are frequently used types, it already has reasonable performance.

Additionally this way a set of classes can define your protocol/allowed attributes, so coding is less "stringy" and "casty".

Classes for the example above look like:

```
  class AuthRequest {
      String user;
      String pwd;
      int array[];
      UserData nestedObject;
  }

  class UserData { String name; int age; String email; }
```

The FST Dson package allows to provide custom mappings during read/write. This way it is possible to e.g.

  * map a string like "10/12/2013" to a Date and vice versa.
  * map arrays to collections and vice versa.

Date Mapping and collection/`HashMap` mapping is built in (see `DsonTypeMapper`).

```
class Example {
    HashMap map; //note: must be an Instantiatable type. Map would fail.
}
```

is mapped to an array with [key,value,key,value] order.

```
example { map: [ "you" user { name: "you" age: 23 } "xyz" 345 ] }
```
or humanized:
```
example { 
  map: [ 
    "you" : user { name: "you" age: 23 } 
    "xyz" : 345  # ':' is optional
  ] 
}
```

decurlyfied ..

```
example  
  map: [ 
    "you" user name: "you" age: 23 ; 
    "xyz" 345
  ] 
;
```

is like

```
Example ex = new Example();
ex.map = new HashMap();
ex.map.put( "you", new User("you", 23) );
ex.map.put( "xyz", 345 );
```

Note Java implementation only:
One can ommit quotes of String's if the target field is of type String (Warning: cannot be read by other languages Dson implementation, but handy when using Dson as DSL with java). (if the string contains whitespace or ;., it still has to get quoted):

```
user name: you age: 23 ; 
```

**Full Code Example**

**Define Custom String type**

**Other languages**

**State of project/test coverage**

**Performance**
