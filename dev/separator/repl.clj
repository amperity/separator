(ns separator.repl
  (:require
    [clj-async-profiler.core :as cap]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [criterium.core :as crit]
    [separator.io :as separator]))


(def genome-scores-file
  (io/file "ml-latest/genome-scores.csv"))


(defn consume!
  [parser]
  (reduce (fn count-row [sum record] (inc sum)) 0 parser))


(defn profile-consumption
  ([file]
   (profile-consumption file 1))
  ([file n]
   (cap/profile
     (dotimes [i n]
       (consume! (separator/read file))))))


(comment
  (crit/quick-bench
    (consume! (separator/read genome-scores-file))))
