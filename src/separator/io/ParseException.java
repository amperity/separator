package separator.io;


import java.util.List;

import clojure.lang.ILookup;
import clojure.lang.IPersistentVector;
import clojure.lang.Keyword;


/**
 * A representation of an error encountered during parsing.
 */
public class ParseException extends RuntimeException implements ILookup {

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

}
