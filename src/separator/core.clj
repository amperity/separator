(ns separator.core
  "Public interface to the Separator parsing and writing logic."
  (:refer-clojure :exclude [read])
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (clojure.lang
      LineNumberingPushbackReader)
    (java.io
      BufferedReader
      EOFException
      File
      FileReader
      InputStream
      InputStreamReader
      Reader
      StringReader
      Writer)
    (java.nio.charset
      StandardCharsets)
    (separator.read
      ParseException
      Parser)))


(def default-options
  "Default parsing options."
  {:separator \,
   :quote \"
   :quote? :required
   :newline :lf
   :escape nil
   :unescape? false
   :max-cell-size 16384
   :max-row-width 2048})


;; ## Reading

(defn- input-reader
  "Convert the given source of input data to a `PushbackReader` that can be fed
  to the parser."
  ^LineNumberingPushbackReader
  [input]
  (cond
    (instance? LineNumberingPushbackReader input)
    input

    (or (instance? BufferedReader input)
        (instance? StringReader input))
    (LineNumberingPushbackReader. input)

    (instance? Reader input)
    (recur (BufferedReader. input))

    (instance? InputStream input)
    (recur (InputStreamReader. input StandardCharsets/UTF_8))

    (instance? File input)
    (recur (FileReader. ^File input StandardCharsets/UTF_8))

    (string? input)
    (recur (StringReader. input))

    (nil? input)
    (throw (IllegalArgumentException. "Can't read data from nil input"))

    :else
    (throw (IllegalArgumentException.
             (str "Don't know how to read data from input type: "
                  (.getName (class input)))))))


(defn parse-error?
  "True if the given value is a parser error."
  [x]
  (instance? ParseException x))


(defn read
  "Parse delimiter-separated row data from the input. Returns an iterable and
  reducible collection where each entry is either a vector of strings
  representing the cells in a single row, or a parse error map with details
  about the error encountered while parsing that row.

  This accepts a variety of inputs, but ultimately constructs a
  `LineNumberingPushbackReader` to read from. No data is read from the input
  until the value returned from this is consumed. The result can only be
  consumed **once**, and will not automatically close the input stream.

  Options may include:

  - `:separator`
    Character which separates row cells.
  - `:quote`
    Character which quotes field contents.
  - `:escape`
    Character which escapes other separator chars in unquoted fields.
  - `:unescape?`
    Whether escape sequences should be replaced with the literal character.
  - `:max-cell-size`
    Limit on number of characters in a single field.
  - `:max-row-width`
    Limit on number of cells in a single row.

  See `default-options` for default values."
  [input & {:as opts}]
  (let [reader (input-reader input)
        opts (merge default-options opts)]
    (Parser.
      reader
      (:max-cell-size opts)
      (:max-row-width opts)
      (:separator opts)
      (:quote opts)
      (if-let [escape (:escape opts)]
        (int escape)
        -1)
      (boolean (:unescape? opts)))))
