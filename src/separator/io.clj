(ns separator.io
  "Public interface to the separator codec."
  (:refer-clojure :exclude [read])
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
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
    (separator.io
      ParseException
      Parser
      TrackingPushbackReader)))


(def default-options
  "Default codec options."
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
  "Convert the given source of input data to a `TrackingPushbackReader` that
  can be fed to the parser."
  ^TrackingPushbackReader
  [input]
  (cond
    (instance? TrackingPushbackReader input)
    input

    (or (instance? BufferedReader input)
        (instance? StringReader input))
    (TrackingPushbackReader. input)

    (instance? Reader input)
    (recur (BufferedReader. input))

    (instance? InputStream input)
    (recur (InputStreamReader. ^InputStream input StandardCharsets/UTF_8))

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

  This accepts a variety of inputs, but ultimately constructs a `TrackingPushbackReader`
  to read from. No data is read from the input until the value returned from
  this is consumed. The result can only be consumed **once**, and will not
  automatically close the input stream.

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
      (int (:separator opts))
      (int (:quote opts))
      (if-let [escape (:escape opts)]
        (int escape)
        -1)
      (boolean (:unescape? opts)))))


;; ## Writing

(defn- should-quote?
  "True if the value string should be quoted based on the separator"
  [^String string separator quote-char quote?]
  (case quote?
    true
    true

    false
    false

    :required
    (or (<= 0 (.indexOf string (int separator)))
        (<= 0 (.indexOf string (int quote-char)))
        ;; CR \r
        (<= 0 (.indexOf string 0x0A))
        ;; LF \n
        (<= 0 (.indexOf string 0x0D)))

    ;; else treat quote? as a predicate
    (quote? string)))


(defn- write-cell
  "Write a single cell to the output writer."
  [^Writer writer value separator quote-char quote?]
  (when (some? value)
    (let [string (str value)]
      (if (should-quote? string separator quote-char quote?)
        (doto writer
          (.write (int quote-char))
          (.write (str/escape string {quote-char (str quote-char quote-char)}))
          (.write (int quote-char)))
        (.write writer string)))))


(defn- write-row
  "Write a full row to the output writer."
  [^Writer writer row separator quote-char quote?]
  (let [started (volatile! false)]
    (reduce
      (fn rf
        [_ cell]
        (if @started
          (.write writer (int separator))
          (vreset! started true))
        (write-cell writer cell separator quote-char quote?))
      nil
      row)))


(defn write
  "Write data to the output `Writer` as separator-delimited text. The `rows`
  should be a reducible collection of sequential cell values. Returns the
  number of rows written.

   Options may include:

   - `:separator`
     Character to separate cells with.
   - `:quote`
     Character to quote cell values with.
   - `:quote?`
     Predicate which should return true for cells to quote. Can be `true` to
     always quote cells, `false` to never quote, and defaults to quoting only
     when necessary.
   - `:newline`
     Keyword option for newlines, either `:lf` for a single `\\n` or `:crlf`
     for Windows-style `\\r\\n`.

  See `default-options` for default values."
  [^Writer output rows & {:as opts}]
  (let [opts (merge default-options opts)
        separator (:separator opts)
        quote-char (:quote opts)
        quote? (:quote? opts)
        row-sep (case (:newline opts)
                  :lf "\n"
                  :crlf "\r\n")
        row-count (reduce
                    (fn write*
                      [n row]
                      (write-row output row separator quote-char quote?)
                      (.write output row-sep)
                      (inc n))
                    0
                    rows)]
    (.flush output)
    row-count))
