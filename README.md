Separator
=========

[![CircleCI](https://circleci.com/gh/amperity/separator.svg?style=shield&circle-token=1b358576395c3758b3a88b5d265862ca91b0fa2b)](https://circleci.com/gh/amperity/separator)
[![codecov](https://codecov.io/gh/amperity/separator/branch/main/graph/badge.svg)](https://codecov.io/gh/amperity/separator)
[![cljdoc](https://cljdoc.org/badge/com.amperity/separator)](https://cljdoc.org/d/com.amperity/separator/CURRENT)

A Clojure library for working with [Delimiter-Separated Value](https://en.wikipedia.org/wiki/Delimiter-separated_values)
data. This includes a highly customizable parser and a simple writer.

You might be interested in using this instead of the common
[clojure.data.csv](https://github.com/clojure/data.csv) or a more mainstream
codec like [Jackson](https://github.com/FasterXML/jackson-dataformats-text/tree/master/csv)
because [CSV is a terrible format](http://fuckcsv.com) and you'll often need to
deal with messy, malformed, and downright bizarre data files.


## Usage

The main namespace entrypoint is `separator.io`, which contains both the
reading and writing interfaces.

```clojure
=> (require '[separator.io :as separator])
```

### Reading

One of the significant features of this library is safety valves on parsing to
deal with bad input data. The parser does its best to recover from these errors
and present meaningful data about the problems to the consumer.

By default, the parser will stop reading a single cell once it exceeds 16KB and
will stop reading a row once it has more than 2,048 cells. These are both
configurable, but should be sane defaults.

The parser also supports customizable quote, separator, and escape characters.
Escapes are not part of the CSV standard but show up often in practice, so we
need to deal with them.

**TODO:** examples

### Writing

**TODO:** examples


## License

Copyright © 2022 Amperity, Inc.

Distributed under the MIT License.
