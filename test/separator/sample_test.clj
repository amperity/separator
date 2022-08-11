(ns separator.sample-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest testing is]]
    [separator.io :as separator])
  (:import
    separator.io.ParseException))


(def sample-dir
  (io/file "test/separator/sample"))


(defn read-sample
  "Return a seq of rows read from the named sample file."
  [file-name & {:as opts}]
  (seq (separator/read-rows (io/file sample-dir file-name) opts)))


(defn parse-err
  "Construct a new parse error."
  [err-type message & {:as opts}]
  (let [err (ParseException.
              (name err-type)
              message
              (:line opts -1)
              (:column opts -1)
              (:partial-cell opts)
              (:skipped-text opts))]
    (when-let [row (:partial-row opts)]
      (.setPartialRow err row))
    err))


(deftest read-samples
  (testing "abc.csv"
    (is (= [["A" "B" "C"]
            ["D" "E" "F"]
            ["G" "H" "I"]]
           (read-sample "abc.csv"))))
  (testing "simple-err.tsv"
    (is (= [["A" "" "C"]
            (parse-err :malformed-quote
                       "Unexpected character following quote: E"
                       :line 2
                       :column 4
                       :partial-cell ""
                       :partial-row ["D"]
                       :skipped-text "E...\"")
            ["G" "H" "I"]
            (parse-err :malformed-quote
                       "Reached end of file while parsing quoted field"
                       :line 5
                       :column 1
                       :partial-cell "K\tL\n"
                       :partial-row ["J"])]
           (read-sample "simple-err.tsv" :separator \tab)))))
