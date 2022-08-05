(ns separator.write-test
  (:require
    [clojure.test :refer [deftest is]]
    [separator.io :as separator]))


(defn write-str
  [rows & {:as opts}]
  (with-out-str
    (separator/write *out* rows opts)))


(deftest csv-generation
  (is (= "a,b,c\n" (write-str [["a" "b" "c"]])))
  (is (= "a,b,c\n1,2,3\n" (write-str [["a" "b" "c"] [1 2 3]])))
  (is (= "a,\"quo\"\"ted\",c\n" (write-str [["a" "quo\"ted" "c"]]))))
