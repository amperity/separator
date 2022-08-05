package separator.io;


import java.io.IOException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import clojure.lang.IFn;
import clojure.lang.IReduceInit;
import clojure.lang.Reduced;
import clojure.lang.Sequential;


/**
 * A defensive delimiter-separated value parser which processes a character
 * stream into a sequence of rows.
 */
public class Parser implements Iterable<Object>, IReduceInit, Sequential {

    public enum Sentinel {
        SEP, EOL, EOF
    }

    public enum ErrorMode {
        IGNORE, INCLUDE, THROW
    }

    // Constant characters
    private static final int lf = (int) '\n';
    private static final int cr = (int) '\r';
    private static final int tab = (int) '\t';
    private static final int eof = -1;

    // Parser configuration
    private final TrackingPushbackReader reader;
    private final int separator;
    private final int quote;
    private final int escape;
    private final boolean unescape;
    private final int maxCellSize;
    private final int maxRowWidth;
    private final ErrorMode errorMode;

    // Internal state
    private Sentinel lastSeenSentinel;


    ///// Constructors /////

    /**
     * Construct a new parser instance.
     */
    public Parser(
            TrackingPushbackReader reader,
            int separator,
            int quote,
            int escape,
            boolean unescape,
            int maxCellSize,
            int maxRowWidth,
            ErrorMode errorMode) {
        this.reader = reader;
        this.separator = separator;
        this.quote = quote;
        this.escape = escape;
        this.unescape = unescape;
        this.maxCellSize = maxCellSize;
        this.maxRowWidth = maxRowWidth;
        this.errorMode = errorMode;
    }


    ///// Internal Implementation /////

    /**
     * Constructs a new ParseException with the provided information.
     */
    private ParseException parseError(String type, String message, StringBuilder cell, boolean skip) throws IOException {
        int line = reader.getLineNumber();
        int column = reader.getColumnNumber();
        String partialCell = cell != null ? cell.toString() : null;
        String skipped = skip ? skipToEol() : null;
        return new ParseException(type, message, line, column, partialCell, skipped);
    }


    /**
     * Given that we have already read a carriage-return character,
     * consume the line-feed character right afterward if any.
     */
    private void consumeCrlf() throws IOException {
        int nextCh = reader.read();
        if (nextCh != lf && nextCh != eof) {
            reader.unread(nextCh);
        }
    }


    /**
     * Takes a character we have just parsed, and returns true iff
     * that character represents a sentinel that signifies we are done
     * parsing the current cell. As a side effect, this method will
     * log said sentinel as the latest seen sentinel, and potentially
     * finish off a CRLF if applicable.
     */
    private boolean checkSentinel(int ch) throws IOException {
        if (ch == separator) {
            lastSeenSentinel = Sentinel.SEP;
            return true;
        } else if (ch == lf) {
            lastSeenSentinel = Sentinel.EOL;
            return true;
        } else if (ch == cr) {
            consumeCrlf();
            lastSeenSentinel = Sentinel.EOL;
            return true;
        } else if (ch == eof) {
            lastSeenSentinel = Sentinel.EOF;
            return true;
        } else {
            return false;
        }
    }


    /**
     * Eats the rest of the current line and returns a string with an
     * abbreviated version of the text that was thrown away.
     */
    private String skipToEol() throws IOException {
        int firstCh = -1;
        int lastCh = -1;
        while (true) {
            int ch = reader.read();
            if (ch == eof) {
                lastSeenSentinel = Sentinel.EOF;
                break;
            } else if (ch == lf) {
                lastSeenSentinel = Sentinel.EOL;
                break;
            } else if (ch == cr) {
                // parse the LF right after, if any
                consumeCrlf();
                lastSeenSentinel = Sentinel.EOL;
                break;
            }
            if (firstCh == -1) {
                firstCh = ch;
            } else {
                lastCh = ch;
            }
        }
        if (firstCh == -1) {
            return "";
        } else if (lastCh == -1) {
            return String.format("%c", firstCh);
        } else {
            return String.format("%c...%c", firstCh, lastCh);
        }
    }


    /**
     * Ensures that we haven't gone over the cell limit, and otherwise
     * throws a parse error.
     */
    private void checkCellLength(StringBuilder cell, int ch) throws IOException {
        if (cell.length() > maxCellSize) {
            if (ch != eof) {
                reader.unread(ch);
            }
            throw parseError(
                "cell-size-exceeded",
                String.format("Data cell exceeded maximum size of %d while reading", maxCellSize),
                cell, true);
        }
    }


    /**
     * Given that we just parsed an escape character, reads the next
     * character and returns a string representing the parsed data.
     */
    private String readEscape() throws IOException {
        int nextCh = reader.read();
        if (this.unescape) {
            switch (nextCh) {
                case 'b':
                    return "\b";
                case 'n':
                    return "\n";
                case 't':
                    return "\t";
                case 'r':
                    return "\r";
                case '0':
                    return Character.toString((char) 0);
                case 'N':
                    // Special case, handled by readText
                    return null;
                default:
                    // drop the escape and return only the escaped character.
                    return Character.toString((char) nextCh);
            }
        } else {
            switch (nextCh) {
                case tab:
                    return String.format("%ct", (char) escape);
                case cr:
                    consumeCrlf();
                case lf:
                    return String.format("%cn", (char) escape);
                case eof:
                    return "";
                default:
                    return String.format("%c%c", (char) escape, (char) nextCh);
            }
        }
    }


    /**
     * Parses a (non-quoted) cell and returns its contents.
     */
    private String readText() throws IOException {
        StringBuilder cell = new StringBuilder();
        for (int ch = reader.read(); !checkSentinel(ch); ch = reader.read()) {
            checkCellLength(cell, ch);
            if (ch == escape) {
                String escaped = readEscape();
                if (escaped == null) {
                    // encountered \N, if this is the entire cell contents,
                    // interpret this as a null.
                    int nextCh = reader.read();
                    if (cell.length() == 0 && checkSentinel(nextCh)) {
                        return null;
                    } else {
                        // otherwise treat it like any other escaped character
                        cell.append("N");
                        reader.unread(nextCh);
                    }
                } else {
                    cell.append(escaped);
                }
            } else {
                cell.append((char) ch);
            }
        }
        return cell.toString();
    }


    /**
     * Given that we have already parsed an opening quote, parses the
     * rest of a quoted cell and returns its contents.
     */
    private String readQuotedText() throws IOException {
        StringBuilder cell = new StringBuilder();
        while (true) {
            int ch = reader.read();
            checkCellLength(cell, ch);
            if (ch == quote) {
                // Encountered a quote character. May be closing or escaped.
                int nextCh = reader.read();
                if (nextCh == quote) {
                    cell.append((char) quote);
                } else if (checkSentinel(nextCh)) {
                    return cell.toString();
                } else {
                    reader.unread(nextCh);
                    throw parseError(
                        "malformed-quote",
                        String.format("Unexpected character following quote: %c", nextCh),
                        cell, true);
                }
            } else if (ch == eof) {
                lastSeenSentinel = Sentinel.EOF;
                throw parseError(
                    "malformed-quote",
                    "Reached end of file while parsing quoted field",
                    cell, false);
            } else {
                cell.append((char) ch);
            }
        }
    }


    /**
     * Parses the next cell and returns its contents.
     */
    private String parseCell() throws IOException {
        int firstCh = reader.read();
        if (firstCh == eof) {
            lastSeenSentinel = Sentinel.EOF;
            return "";
        } else if (firstCh == quote) {
            return readQuotedText();
        } else {
            reader.unread(firstCh);
            return readText();
        }
    }


    /**
     * Parses the next row and returns it as a list of strings.
     */
    private List<String> parseRow() throws IOException {
        ArrayList<String> row = new ArrayList<String>();
        try {
            while (true) {
                String cell = parseCell();
                if (lastSeenSentinel == Sentinel.EOF && row.isEmpty() && cell.equals("")) {
                    return null;
                }
                row.add(cell);
                if (lastSeenSentinel != Sentinel.SEP) {
                    return row;
                }
                if (row.size() >= maxRowWidth) {
                    throw parseError(
                        "row-size-exceeded",
                        String.format("Data row exceeded maximum cell count of %d while reading", maxRowWidth),
                        null, true);
                }
            }
        } catch (ParseException e) {
            e.setPartialRow(row);
            throw e;
        }
    }


    ///// Iterable /////

    public class RowIterator implements Iterator<Object> {

        private Object next;
        private boolean done;

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            } else if (done) {
                return false;
            } else {
                while (true) {
                    try {
                        next = parseRow();
                        if (lastSeenSentinel == Sentinel.EOF) {
                            done = true;
                        }
                        return next != null;
                    } catch (ParseException e) {
                        if (lastSeenSentinel == Sentinel.EOF) {
                            done = true;
                        }
                        if (errorMode == ErrorMode.IGNORE) {
                            next = null;
                        } else if (errorMode == ErrorMode.INCLUDE) {
                            next = e;
                            return true;
                        } else if (errorMode == ErrorMode.THROW) {
                            throw e;
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        @Override
        public Object next() throws NoSuchElementException {
            if (hasNext()) {
                Object o = next;
                next = null;
                return o;
            } else {
                throw new NoSuchElementException("No more rows");
            }
        }
    }


    @Override
    public Iterator<Object> iterator() {
        return new RowIterator();
    }


    ///// IReduceInit /////

    @Override
    public Object reduce(IFn fn, Object init) {
        Object acc = init;
        for (Object row : this) {
            acc = fn.invoke(acc, row);
            if (acc instanceof Reduced) {
                return ((Reduced) acc).deref();
            }
        }
        return acc;
    }

}
