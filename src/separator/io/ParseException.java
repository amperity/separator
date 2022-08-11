package separator.io;


import java.util.List;

import clojure.lang.IExceptionInfo;
import clojure.lang.ILookup;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;


/**
 * A representation of an error encountered during parsing.
 */
public class ParseException extends RuntimeException implements ILookup, IExceptionInfo {

    /**
     * Type of error encountered.
     */
    private final Keyword type;

    /**
     * The line of input the error occurred on.
     */
    private final int line;

    /**
     * The column of input the error occurred on.
     */
    private final int column;

    /**
     * The partial cell data read up to the error, if applicable.
     */
    private final String partialCell;

    /**
     * A partial collection of cells read before a row-level error.
     */
    private IPersistentVector partialRow;

    /**
     * Any text skipped while trying to recover from the error, if applicable.
     */
    private final String skippedText;


    ///// Constructors /////

    /**
     * Construct a new parse error with full information.
     */
    public ParseException(
            String type,
            String message,
            int line,
            int column,
            String partialCell,
            String skippedText) {
        super(message);
        this.type = Keyword.intern(null, type);
        this.line = line;
        this.column = column;
        this.partialCell = partialCell;
        this.skippedText = skippedText;
    }


    /**
     * Set the partialRow field, once.
     */
    public void setPartialRow(IPersistentVector row) {
        if (partialRow != null) {
            throw new IllegalStateException("The partialRow field is already set");
        }
        this.partialRow = row;
    }


    ///// Object /////

    @Override
    public boolean equals(Object x) {
        if (this == x) return true;
        if (!(x instanceof ParseException)) return false;
        ParseException e = (ParseException)x;
        return getData().equals(e.getData());
    }


    @Override
    public int hashCode() {
        return (31 * getClass().hashCode()) + getData().hashCode();
    }


    @Override
    public String toString() {
        return String.format("%s %d:%d", type.toString(), line, column);
    }


    ///// ILookup /////

    @Override
    public Object valAt(Object k) {
        return valAt(k, null);
    }


    @Override
    public Object valAt(Object k, Object notFound) {
        if (!(k instanceof Keyword)) {
            return notFound;
        }

        Keyword kw = (Keyword)k;

        if (kw.getNamespace() != null) {
            return notFound;
        }

        switch (kw.getName()) {
            case "type":
                return type;
            case "message":
                return getMessage();
            case "line":
                return line;
            case "column":
                return column;
            case "partial-cell":
                return partialCell;
            case "partial-row":
                return partialRow;
            case "skipped-text":
                return skippedText;
            default:
                return notFound;
        }
    }


    ///// IExceptionInfo /////

    @Override
    public IPersistentMap getData() {
        int length = 8
            + (partialCell != null ? 2 : 0)
            + (partialRow != null ? 2 : 0)
            + (skippedText != null ? 2 : 0);
        Object[] elements = new Object[length];
        int idx = 0;

        elements[idx++] = Keyword.intern(null, "type");
        elements[idx++] = type;
        elements[idx++] = Keyword.intern(null, "message");
        elements[idx++] = getMessage();
        elements[idx++] = Keyword.intern(null, "line");
        elements[idx++] = line;
        elements[idx++] = Keyword.intern(null, "column");
        elements[idx++] = column;

        if (partialCell != null) {
            elements[idx++] = Keyword.intern(null, "partial-cell");
            elements[idx++] = partialCell;
        }

        if (partialRow != null) {
            elements[idx++] = Keyword.intern(null, "partial-row");
            elements[idx++] = partialRow;
        }

        if (skippedText != null) {
            elements[idx++] = Keyword.intern(null, "skipped-text");
            elements[idx++] = skippedText;
        }

        return PersistentArrayMap.createAsIfByAssoc(elements);
    }

}
