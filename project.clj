(defproject com.amperity/commaeleon "0.1.0-SNAPSHOT"
  :description "An efficient and defensive delimiter-separated-value parser."
  :url "https://github.com/amperity/commaeleon"
  :license {:name "MIT License"
            :url "https://mit-license.org/"}

  :dependencies
  [[org.clojure/clojure "1.11.1"]]

  :profiles
  {:repl
   {:source-paths ["dev"]
    :repl-options {:init-ns commaeleon.repl}
    :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
    :dependencies [[org.clojure/tools.namespace "1.1.0"]]}})
