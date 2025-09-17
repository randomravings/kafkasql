package kafkasql.core;

import java.io.Writer;

import kafkasql.core.ast.Ast;

import java.io.IOException;

public abstract class Printer {

    private static final String SPACE = " ";
    private static final String TAB = "  ";
    private static final String NEW_LINE = "\n";
    private static final String DOT = ".";
    private static final String COMMA = ",";
    private static final String COLON = ":";
    private static final String SEMICOLON = ";";
    private static final String LPAREN = "(";
    private static final String RPAREN = ")";
    private static final String LBRACKET = "[";
    private static final String RBRACKET = "]";
    private static final String LBRACE = "{";
    private static final String RBRACE = "}";
    private static final String LT = "<";
    private static final String GT = ">";

    private final Writer out;

    protected Printer(Writer out) {
        this.out = out;
    }

    public abstract void write(Ast ast) throws IOException;

    protected void write(String s) throws IOException {
        out.write(s);
    }

    protected void write(char c) throws IOException {
        out.write(c);
    }

    protected void write(int i) throws IOException {
        out.write(Integer.toString(i));
    }

    protected void write(long i) throws IOException {
        out.write(Long.toString(i));
    }

    protected void nil() throws IOException {
        write("<nil>");
    }

    protected void empty() throws IOException {
        write("<empty>");
    }

    protected void write(boolean b) throws IOException {
        write(Boolean.toString(b));
    }

    public void writeSq(String s) throws IOException {
        write("'");
        write(s);
        write("'");
    }

    protected void space() throws IOException {
        write(SPACE);
    }

    protected void indent(int indent) throws IOException {
        for (int i = 0; i < indent; i++) {
            write(TAB);
        }
    }

    protected void newLine() throws IOException {
        write(NEW_LINE);
    }

    protected void dot() throws IOException {
        write(DOT);
    }

    protected void comma() throws IOException {
        write(COMMA);
    }

    protected void colon() throws IOException {
        write(COLON);
    }

    protected void semicolon() throws IOException {
        write(SEMICOLON);
    }

    protected void lparen() throws IOException {
        write(LPAREN);
    }

    protected void rparen() throws IOException {
        write(RPAREN);
    }

    protected void lbracket() throws IOException {
        write(LBRACKET);
    }

    protected void rbracket() throws IOException {
        write(RBRACKET);
    }

    protected void lbrace() throws IOException {
        write(LBRACE);
    }

    protected void rbrace() throws IOException {
        write(RBRACE);
    }

    protected void lt() throws IOException {
        write(LT);
    }

    protected void gt() throws IOException {
        write(GT);
    }

    protected void writeAll(String sep, String... ss) throws IOException {
        if (ss.length == 0) return;
        write(ss[0]);
        for (int i = 1; i < ss.length; i++) {
            write(sep);
            write(ss[i]);
        }
    }
}