(ns separator.repl
  (:require
    [clj-async-profiler.core :as prof]
    [clojure.data.csv :as data.csv]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [criterium.core :as crit]
    [separator.io :as separator])
  (:import
    (com.fasterxml.jackson.dataformat.csv
      CsvMapper
      CsvParser$Feature)))


(defn consume!
  "Consume all values from the given collection by reducing over it. Returns a
  count of the read records."
  [coll]
  (reduce (fn count-row [sum record] (inc sum)) 0 coll))


(defn data-csv-read
  "Read the given file with clojure.data.csv."
  [file]
  (let [reader (io/reader file)]
    (data.csv/read-csv reader)))


(defn jackson-read
  "Read the given file with Jackson."
  [file]
  (iterator-seq
    (.. (CsvMapper.)
        (readerForListOf String)
        (with CsvParser$Feature/WRAP_AS_ARRAY)
        (readValues file))))


(defn profile-consumption
  "Profile reading the given file with separator, optionally more than once."
  ([file]
   (profile-consumption file 1))
  ([file n]
   (prof/profile
     (dotimes [i n]
       (consume! (separator/read-rows file))))))
