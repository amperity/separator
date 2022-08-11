(ns separator.sample-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest testing is]]
    [separator.io :as separator]))


(def sample-dir
  (io/file "test/separator/sample"))


(defn read-sample
  "Return a seq of rows read from the named sample file."
  [file-name & {:as opts}]
  (seq (separator/read-rows (io/file sample-dir file-name) opts)))


(deftest read-samples
  (testing "abc.csv"
    (is (= [["A" "B" "C"]
            ["D" "E" "F"]
            ["G" "H" "I"]]
           (read-sample "abc.csv")))))
