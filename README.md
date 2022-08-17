Separator
=========

[![CircleCI](https://circleci.com/gh/amperity/separator.svg?style=shield&circle-token=1b358576395c3758b3a88b5d265862ca91b0fa2b)](https://circleci.com/gh/amperity/separator)
[![codecov](https://codecov.io/gh/amperity/separator/branch/main/graph/badge.svg)](https://codecov.io/gh/amperity/separator)
[![cljdoc](https://cljdoc.org/badge/com.amperity/separator)](https://cljdoc.org/d/com.amperity/separator/CURRENT)

A Clojure library for working with [Delimiter-Separated Value](https://en.wikipedia.org/wiki/Delimiter-separated_values)
data. This includes a customizable defensive parser and a simple writer.

You might be interested in using this instead of the common
[clojure.data.csv](https://github.com/clojure/data.csv) or a more mainstream
codec like [Jackson](https://github.com/FasterXML/jackson-dataformats-text/tree/master/csv)
because [CSV is a terrible format](http://fuckcsv.com) and you'll often need to
deal with messy, malformed, and downright bizarre data files.


## Usage

Releases are published on Clojars; to use the latest version with Leiningen,
add the following to your project dependencies:

[![Clojars Project](http://clojars.org/com.amperity/separator/latest-version.svg)](http://clojars.org/com.amperity/separator)

The main namespace entrypoint is `separator.io`, which contains both the
reading and writing interfaces.

```clojure
=> (require '[separator.io :as separator])
```

### Reading

One of the significant features of this library is safety valves on parsing to
deal with bad input data. The parser does its best to recover from these errors
and present meaningful data about the problems to the consumer. This includes
limiting the maximum cell size and the maximum row width.

To parse data into a sequence of rows, use the `read-rows` function. This
accepts many kinds of inputs, including directly reading string data:

```clojure
=> (vec (separator/read-rows "A,B,C\nD,E,F\nG,H,I\n"))
[["A" "B" "C"] ["D" "E" "F"] ["G" "H" "I"]]

;; quoted cells can embed newlines
=> (vec (separator/read-rows "A,B,C\nD,E,\"F\nG\",H,I\n"))
[["A" "B" "C"] ["D" "E" "F\nG" "H" "I"]]

;; parse errors are included in the sequence by default
=> (vec (separator/read-rows "A,B,C\nD,\"\"E,F\nG,H,I\n"))
[["A" "B" "C"] #<separator.io.ParseException@34b69fbe :malformed-quote 2:4> ["G" "H" "I"]]

;; the error mode can also omit them
=> (vec (separator/read-rows "A,B,C\nD,\"\"E,F\nG,H,I\n" :error-mode :ignore))
[["A" "B" "C"] ["G" "H" "I"]]

;; ...or throw them
=> (vec (separator/read-rows "A,B,C\nD,\"\"E,F\nG,H,I\n" :error-mode :throw))
;; Execution error (ParseException) at separator.io.Parser/parseError (Parser.java:87).
;; Unexpected character following quote: E

;; the errors carry data:
=> (ex-data *e)
{:column 4,
 :line 2,
 :message "Unexpected character following quote: E",
 :partial-cell "",
 :partial-row ["D"],
 :skipped-text "E...F",
 :type :malformed-quote}
```

The parser also supports customizable quote, separator, and escape characters.
Escapes are not part of the CSV standard but show up often in practice, so we
need to deal with them.

```clojure
=> (vec (separator/read-rows "A|B|C\nD|E|^F\nG^|H|I\n" :separator \| :quote \^))
[["A" "B" "C"] ["D" "E" "F\nG" "H" "I"]]

=> (vec (separator/read-rows "A,B,C\\\nD,E,F\nG,H,I\n" :escape \\))
[["A" "B" "C\\nD" "E" "F"] ["G" "H" "I"]]
```

Additionally, there's a convenience wrapper using the `zip-headers` transducer
to read a sequence of map records instead, by utilizing a row of headers:

```clojure
=> (vec (separator/read-records "name,age,role\nPhillip Fry,26,Delivery Boy\nTuranga Leela,28,Ship Pilot\nHubert Farnsworth,160,Professor\n"))
[{"age" "26", "name" "Phillip Fry", "role" "Delivery Boy"}
 {"age" "28", "name" "Turanga Leela", "role" "Ship Pilot"}
 {"age" "160", "name" "Hubert Farnsworth", "role" "Professor"}]
```

### Writing

The library also provides tools for writing delimiter-separated data from a
sequence of rows using the `write-rows` function. This takes a `Writer` to print the
data to and a similar set of options to control the output format:

```clojure
=> (separator/write-rows *out* [["A" "B" "C"] ["D" "E" "F"] ["G" "H" "I"]])
;; A,B,C
;; D,E,F
;; G,H,I
3

;; cells containing the quote or separator character are automatically quoted
=> (separator/write-rows *out* [["A" "B,B" "C"] ["D" "E" "F\"F"]])
;; A,"B,B",C
;; D,E,"F""F"
2

;; you can also force quoting for all cells
=> (separator/write-rows *out* [["A" "B" "C"] ["D" "E" "F"] ["G" "H" "I"]] :quote? true)
;; "A","B","C"
;; "D","E","F"
;; "G","H","I"
3

;; or provide a predicate to control quoting
=> (separator/write-rows *out* [["A" "B" "C"] ["D" "E" "F"] ["G" "H" "I"]] :quote? #{"E"})
;; A,B,C
;; D,"E",F
;; G,H,I
3
```


## Performance

Separator prioritizes defensiveness over speed, but aims to be as performant as
possible within those constraints. For comparison, it's faster than `data.csv`
but significantly slower than Jackson:

```
=> (crit/quick-bench (consume! (separator/read-rows test-file)))
Evaluation count : 6 in 6 samples of 1 calls.
             Execution time mean : 5.544234 sec
    Execution time std-deviation : 78.630488 ms
   Execution time lower quantile : 5.481820 sec ( 2.5%)
   Execution time upper quantile : 5.667485 sec (97.5%)
                   Overhead used : 6.824396 ns

=> (crit/quick-bench (consume! (data-csv-read test-file)))
Evaluation count : 6 in 6 samples of 1 calls.
             Execution time mean : 10.253641 sec
    Execution time std-deviation : 121.221011 ms
   Execution time lower quantile : 10.146078 sec ( 2.5%)
   Execution time upper quantile : 10.436205 sec (97.5%)
                   Overhead used : 6.943926 ns

=> (crit/quick-bench (consume! (jackson-read test-file)))
Evaluation count : 6 in 6 samples of 1 calls.
             Execution time mean : 2.325301 sec
    Execution time std-deviation : 40.611328 ms
   Execution time lower quantile : 2.296693 sec ( 2.5%)
   Execution time upper quantile : 2.390772 sec (97.5%)
                   Overhead used : 6.824396 ns
```

The test above was performed on a 2021 MacBook Pro with `data.csv` version
1.0.1 and `jackson-dataformat-csv` version 2.13.0 on a 330 MB CSV file with
12.4 million rows.

Of course, all the speed in the world won't save you from a misplaced quote:

```
=> (spit "simple-err.csv" "A,B,C\nD,\"\"E,F\nG,H,I\n")
nil

=> (consume! (separator/read-rows (io/file "simple-err.csv")))
3

=> (consume! (data-csv-read (io/file "simple-err.csv")))
Execution error at clojure.data.csv/read-quoted-cell (csv.clj:37).
CSV error (unexpected character: E)

=> (consume! (jackson-read (io/file "simple-err.csv")))
Execution error (JsonParseException) at com.fasterxml.jackson.core.JsonParser/_constructError (JsonParser.java:2337).
Unexpected character ('E' (code 69)): Expected column separator character (',' (code 44)) or end-of-line
 at [Source: (com.fasterxml.jackson.dataformat.csv.impl.UTF8Reader); line: 2, column: 6]
```


## License

Copyright © 2022 Amperity, Inc.

Distributed under the MIT License.
