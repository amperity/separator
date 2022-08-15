(ns separator.write-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [separator.io :as separator]))


(defn write-str
  [rows & {:as opts}]
  (with-out-str
    (separator/write-rows *out* rows opts)))


(deftest csv-generation
  (is (= "a,b,c\n" (write-str [["a" "b" "c"]])))
  (is (= "a,b,c\n1,2,3\n" (write-str [["a" "b" "c"] [1 2 3]])))
  (is (= "a,\"quo\"\"ted\",c\n" (write-str [["a" "quo\"ted" "c"]]))))


(deftest newline-separator
  (testing "line-feed"
    (is (= "a,b,c\n1,2,3\n" (write-str [["a" "b" "c"] [1 2 3]] :newline :lf))))
  (testing "carriage-return line-feed"
    (is (= "a,b,c\r\n1,2,3\r\n" (write-str [["a" "b" "c"] [1 2 3]] :newline :crlf))))
  (testing "bad option"
    (is (thrown? IllegalArgumentException
          (write-str [["a" "b" "c"] [1 2 3]] :newline :boo)))))


(deftest quoting-modes
  (testing "never"
    (is (= "a,b,c\nd,e,f\n"
           (write-str [["a" "b" "c"] ["d" "e" "f"]] :quote? false))
        "should not quote")
    (is (= "a,b,c\nd,e\"e,f\n"
           (write-str [["a" "b" "c"] ["d" "e\"e" "f"]] :quote? false))
        "should not quote, even if required"))
  (testing "always"
    (is (= "\"a\",\"b\",\"c\"\n\"d\",\"e\",\"f\"\n"
           (write-str [["a" "b" "c"] ["d" "e" "f"]] :quote? true))
        "should quote all cells"))
  (testing "required"
    (is (= "a,\"b,b\",c\nd,\"e\"\"e\",f\n"
           (write-str [["a" "b,b" "c"] ["d" "e\"e" "f"]] :quote? :required))
        "should quote when required"))
  (testing "predicate"
    (is (= "a,\"b\",c\nd,e,\"f\"\n"
           (write-str [["a" "b" "c"] ["d" "e" "f"]] :quote? #{"b" "f"}))
        "should quote when truthy")))
