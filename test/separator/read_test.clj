(ns separator.read-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest testing is]]
    [separator.io :as separator]))


(defn read-csv
  [input & {:as opts}]
  (seq (separator/read input opts)))


(deftest empty-inputs
  (is (nil? (read-csv "")))
  (is (= [[""]] (read-csv "\n")))
  (is (= [[""]] (read-csv "\r\n"))))


(deftest line-endings
  (is (= [["foo"]] (read-csv "foo")))
  (is (= [["foo"]] (read-csv "foo\r")))
  (is (= [["foo"]] (read-csv "foo\n")))
  (is (= [["foo"]] (read-csv "foo\r\n")))
  (is (= [["foo"] ["bar"]] (read-csv "foo\rbar")))
  (is (= [["foo"] ["bar"]] (read-csv "foo\nbar")))
  (is (= [["foo"] ["bar"]] (read-csv "foo\r\nbar"))))


(deftest custom-separators
  (is (= [["abc" "def" "ghi"] ["jkl" "mno"]]
         (read-csv "abc,def,ghi\r\njkl,mno\r\n" :separator \,)))
  (is (= [["123" "456" "789"] ["abc" "def" "ghi"]]
         (read-csv "123\t456\t789\nabc\tdef\tghi\n" :separator \tab)))
  (is (= [["xyz" "123" ",\t "]]
         (read-csv "xyz|123|,\t \n" :separator \|))))


(deftest escape-chars
  (is (= [["a" "b" "c"] ["john\\tdoe" "1986" "zip"] ["Jef\\nFoo" "1981" "row"]]
         (read-csv "a\tb\tc\njohn\\\tdoe\t1986\tzip\nJef\\\nFoo\t1981\trow\n"
                   :separator \tab
                   :escape \\))
      "omniture-style escape characters embed separators")
  (is (= [["a" "b\\nc" "d"]]
         (read-csv "a\tb\\\r\nc\td\r\n" :separator \tab :escape \\))
      "crlf embeds as '\\n'")
  (is (= [["a" "b\\x" "c"]]
         (read-csv "a\tb\\x\tc\n" :separator \tab :escape \\))
      "unrecognized escape embeds as read"))


(deftest quoted-cells
  (is (= [["A" "B" "C"]]
         (read-csv "\"A\",\"B\",\"C\""))
      "fully-quoted cell row")
  (is (= [["A" "B" "C"]]
         (read-csv "A,\"B\",C"))
      "quoted cell among non-quoted cells")
  (is (= [["A" "B\r\nC" "D"]]
         (read-csv "A,\"B\r\nC\",\"D\"\n")))
  (is (= [["A" "B\"C" "D"]]
         (read-csv "A,\"B\"\"C\",\"D\""))
      "embedded escaped quote character")
  (testing "custom quote char"
    (is (= [["A" "B" "C^D" "E^F"]]
           (read-csv "A,^B^,C^D,^E^^F^\n" :quote \^)))))


(deftest malformed-quoted-cells
  (is (= [["A" "badly\" quoted" "cell"]]
         (read-csv "A,badly\" quoted,cell"))
      "parser is tolerant of quotes mid-cell")
  (testing "hanging quote to next row"
    (let [[row1 err row2]
          (read-csv "foo,bar,baz\r\nwell,\"open quote\r\nthen,\"some proper field\",still bad\r\nabc,xyz\r\n")]
      (is (= ["foo" "bar" "baz"] row1) "first row parses correctly")
      (is (= ["abc" "xyz"] row2) "last row parses correctly")
      (is (= :malformed-quote (:type err)) "middle row returns a parse error")
      (is (= ["well"] (:partial-row err)))
      (is (= "open quote\r\nthen," (:partial-cell err)))
      (is (= "s...d" (:skipped-text err)))))
  (testing "hanging quote read to eof"
    (let [[row1 err] (read-csv "foo,bar,baz\nA,\"this is a problem, cell...\n")]
      (is (= ["foo" "bar" "baz"] row1)
          "first row parses correctly")
      (is (= :malformed-quote (:type err)))
      (is (= ["A"] (:partial-row err)))
      (is (= "this is a problem, cell...\n" (:partial-cell err))))))


(deftest cell-size-limits
  (testing "unquoted overflow"
    (let [[row1 err row2] (read-csv "abc,def,ghi\nabcdefghijklmnopqrstuvwxyz,123,456\njkl,mno,pqr\n"
                                    :max-cell-size 20)]
      (is (= ["abc" "def" "ghi"] row1) "first row parses correctly")
      (is (= ["jkl" "mno" "pqr"] row2) "last row parses correctly")
      (is (= :cell-size-exceeded (:type err)) "oversize cell results in parse error")
      (is (= "abcdefghijklmnopqrstu" (:partial-cell err)))
      (is (= "v...6" (:skipped-text err)))))
  (testing "quoted overflow"
    (let [[err row] (read-csv "abc,def,\"ghi\nabc,123,456\njkl,mno,pqr\nfoo,bar,baz"
                              :max-cell-size 20)]
      (is (= ["foo" "bar" "baz"] row) "following row parses correctly")
      (is (= :cell-size-exceeded (:type err)) "oversize cell results in parse error")
      (is (= "ghi\nabc,123,456\njkl,m" (:partial-cell err)))
      (is (= "n...r" (:skipped-text err)))))
  (testing "rare edge cases in EOL / EOF placement"
    (doseq [[n input-str partial-cell skipped-text]
            [[21 "abc,def,\"abcdefghijklmnopqrst\n" "abcdefghijklmnopqrst\n" ""]
             [22 "abc,def,\"abcdefghijklmnopqrstu\n" "abcdefghijklmnopqrstu" ""]
             [23 "abc,def,\"abcdefghijklmnopqrstuv\n" "abcdefghijklmnopqrstu" "v"]
             [24 "abc,def,\"abcdefghijklmnopqrstuvw\n" "abcdefghijklmnopqrstu" "v...w"]]]
      (testing (str "- " n)
        (let [[err & more] (read-csv input-str :max-cell-size 20)]
          (is (empty? more) "no more results")
          (is (= :cell-size-exceeded (:type err)))
          (is (= partial-cell (:partial-cell err)))
          (is (= skipped-text (:skipped-text err))))))))


(deftest row-size-limits
  (let [[row1 err row2] (read-csv "abc,def,ghi\njkl,mno,pqr,s\nvwx,yz1"
                                  :max-row-width 3)]
    (is (= ["abc" "def" "ghi"] row1) "first row parses correctly")
    (is (= ["vwx" "yz1"] row2) "last row parses correctly")
    (is (= :row-size-exceeded (:type err)))
    (is (= ["jkl" "mno" "pqr"] (:partial-row err)))
    (is (= "s" (:skipped-text err)))))
