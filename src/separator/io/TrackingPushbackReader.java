package separator.io;


import java.io.IOException;
import java.io.FilterReader;
import java.io.Reader;


/**
 * A {@code}PushbackReader{@code} which tracks the input line and column position.
 *
 * Unfortunately we can't use Clojure's {@code}LineNumberingPushbackReader{@code}
 * here, because it's based on Java's {@code}LineNumberReader{@code} which
 * transparently replaces {@code}\r{@code} and {@code}\r\n{@code} with
 * {@code}\n{@code}. This is fine between rows, but results in unacceptable
 * changes to the raw content of quoted cells.
 *
 * As an additional optimization, we directly implement minimal one-character
 * pushback logic so we can reduce the overhead of the more flexible and
 * thread-safe {@code}PushbackReader{@code}. This class ONLY correctly
 * implements the {@code}read(){@code} and {@code}unread(int){@code} methods,
 * as that is all the parser uses.
 */
public class TrackingPushbackReader extends FilterReader {

    /** Pushback character. */
    private int last = -1;

    /** Was the last thing we read a carriage return? */
    private boolean inEOL = false;

    /** Current line the reader is on. */
    private int line = 0;

    /** Current column the reader is on. */
    private int column = 0;


    /**
     * Construct a new tracking pushback reader.
     *
     * @param reader  source data reader
     */
    public TrackingPushbackReader(Reader reader){
        super(reader);
    }


    /**
     * Get the current line position of the reader.
     *
     * @return line number, 1-indexed
     */
    public int getLineNumber(){
        return line + 1;
    }


    /**
     * Get the current column position of the reader.
     *
     * @return column number, 1-indexed
     */
    public int getColumnNumber(){
        return column + 1;
    }


    @Override
    public boolean markSupported() {
        return false;
    }


    @Override
    public int read() throws IOException {
        int c;

        if (last == -1) {
            c = super.read();
        } else {
            c = last;
            last = -1;
        }

        if (c == '\r' || c == '\n') {
            inEOL = true;
        } else if (inEOL) {
            inEOL = false;
            line++;
            column = 0;
        } else {
            column++;
        }

        return c;
    }


    /**
     * Pushes back a single character by copying it to the front of the
     * pushback buffer.
     */
    public void unread(int c) throws IOException {
        if (last != -1) {
            throw new IOException("Pushback buffer overflow");
        }
        last = c;
        column--;
    }
}
