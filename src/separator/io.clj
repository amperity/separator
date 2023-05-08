(ns separator.io
  "Public interface to the separator codec."
  (:require
    [clojure.string :as str])
  (:import
    (java.io
      BufferedReader
      File
      FileInputStream
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
      Parser$ErrorMode
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
   :max-row-width 2048
   :error-mode :include})


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
    (recur (FileInputStream. ^File input))

    (string? input)
    (recur (StringReader. input))

    (nil? input)
    (throw (IllegalArgumentException. "Can't read data from nil input"))

    :else
    (throw (IllegalArgumentException.
             (str "Don't know how to read data from input type: "
                  (.getName (class input)))))))


(defn- parser-error-mode
  "Convert a keyword to an `ErrorMode` enum value."
  ^Parser$ErrorMode
  [k]
  (case k
    :ignore Parser$ErrorMode/IGNORE
    :include Parser$ErrorMode/INCLUDE
    :throw Parser$ErrorMode/THROW
    (throw (IllegalArgumentException.
             (str "Unknown error mode setting: " (pr-str k))))))


(defn parse-error?
  "True if the given value is a parser error."
  [x]
  (instance? ParseException x))


(defn zip-headers
  "A transducer which will zip up rows of cell data into record maps. If
  headers are provided, they will be used directly, otherwise this will treat
  the first row as headers."
  ([]
   (zip-headers nil))
  ([headers]
   (let [state (volatile! headers)]
     (fn xf
       [rf]
       (fn zip
         ([] (rf))
         ([acc] (rf acc))
         ([acc row]
          (if-let [headers @state]
            (if (parse-error? row)
              (rf acc row)
              (let [row-meta (meta row)
                    record (zipmap headers row)
                    record-with-meta (with-meta record row-meta)]
                (rf acc record-with-meta)))
            (if (parse-error? row)
              (throw row)
              (do
                (vreset! state row)
                acc)))))))))


(defn read-rows
  "Parse delimiter-separated row data from the input. Returns a parser, which
  is an iterable and reducible collection where each entry is either a vector
  of strings representing the cells in a single row, or a parse error with
  details about the error encountered while parsing that row.

  This accepts a variety of input types, including `String`, `File`,
  `InputStream`, and `Reader` values. No data is read from the input until the
  collection is consumed. The parser can only be consumed **once**, and will
  not automatically close the input.

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
  - `:error-mode`
    One of the following values:
      - `:ignore` to ignore all parsing errors
      - `:include` to include errors in the parsed sequence as values
      - `:throw` to throw as errors are encountered

  See `default-options` for default values."
  [input & {:as opts}]
  (let [opts (merge default-options opts)]
    (Parser.
      (input-reader input)
      (int (:separator opts))
      (int (:quote opts))
      (if-let [escape (:escape opts)]
        (int escape)
        -1)
      (boolean (:unescape? opts))
      (:max-cell-size opts)
      (:max-row-width opts)
      (parser-error-mode (:error-mode opts)))))


(defn read-records
  "Parse delimiter-separated row data from the input, as in `read-rows`. This
  function returns a wrapped parser which will convert all rows into record
  maps by applying headers.

  Options the same as `read-rows`, with the addition of:

  - `:headers`
    A known sequence of header values to use for the row data. If not provided,
    they will be read from the first row of input."
  [input & {:as opts}]
  (eduction
    (zip-headers (:headers opts))
    (read-rows input opts)))


;; ## Writing

(defn- row-separator
  "Convert a keyword to row separator string."
  ^String
  [k]
  (case k
    :lf "\n"
    :crlf "\r\n"
    (throw (IllegalArgumentException.
             (str "Unknown row separator setting: " (pr-str k))))))


(defn- should-quote?
  "True if the value string should be quoted based on the separator"
  [^String string separator quote-char quote?]
  (case quote?
    false
    false

    true
    true

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


(defn write-rows
  "Write data to the output `Writer` as separator-delimited text. The `rows`
  should be a reducible collection of sequences of cell values. Each cell is
  converted to a string with `str` before writing.

  Returns the number of rows written.

  Options may include:

  - `:separator`
    Character to separate cells with.
  - `:quote`
    Character to quote cell values with.
  - `:quote?`
    Controls whether cells are quoted. May be `true` to always quote cells,
    `false` to never quote, or a predicate which should return truthy on cell
    values that need quoting. Defaults to quoting only when necessary.
  - `:newline`
    Keyword option for newlines, either `:lf` for a single `\\n` or `:crlf`
    for Windows-style `\\r\\n`.

  See `default-options` for default values."
  [^Writer output rows & {:as opts}]
  (let [opts (merge default-options opts)
        separator (:separator opts)
        quote-char (:quote opts)
        quote? (:quote? opts)
        row-sep (row-separator (:newline opts))
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
