// Generated from /Users/bergur/GitHub/randomravings/kafkasql/src/main/antlr/SqlStreamParser.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class SqlStreamParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		INCLUDE=1, CREATE=2, CONTEXT=3, USE=4, SCALAR=5, ENUM=6, STRUCT=7, UNION=8, 
		STREAM=9, READ=10, WRITE=11, FROM=12, TO=13, LOG=14, COMPACT=15, TYPE=16, 
		WHERE=17, AS=18, OPTIONAL=19, VALUES=20, OR=21, AND=22, IS=23, NOT=24, 
		DEFAULT=25, TRUE=26, FALSE=27, NULL=28, BOOL=29, INT8=30, UINT8=31, INT16=32, 
		UINT16=33, INT32=34, UINT32=35, INT64=36, UINT64=37, SINGLE=38, DOUBLE=39, 
		DECIMAL=40, STRING=41, FSTRING=42, BYTES=43, FBYTES=44, UUID=45, DATE=46, 
		TIME=47, TIMESTAMP=48, TIMESTAMP_TZ=49, LIST=50, MAP=51, STAR=52, COMMA=53, 
		DOT=54, COLON=55, LPAREN=56, RPAREN=57, LBRACK=58, RBRACK=59, LT=60, GT=61, 
		EQ=62, NEQ=63, LTE=64, GTE=65, SEMI=66, STRING_LIT=67, NUMBER=68, ID=69, 
		WS=70, COMMENT=71, WS_PATH=72, FILE_PATH=73, BAD_PATH_CHAR=74;
	public static final int
		RULE_script = 0, RULE_includeSection = 1, RULE_includePragma = 2, RULE_filePath = 3, 
		RULE_statement = 4, RULE_useStmt = 5, RULE_useContext = 6, RULE_dmlStmt = 7, 
		RULE_readStmt = 8, RULE_streamName = 9, RULE_typeBlock = 10, RULE_projection = 11, 
		RULE_projectionItem = 12, RULE_whereClause = 13, RULE_writeStmt = 14, 
		RULE_fieldPathList = 15, RULE_fieldPath = 16, RULE_pathSeg = 17, RULE_tuple = 18, 
		RULE_literalOnlyList = 19, RULE_valueLit = 20, RULE_ddlStmt = 21, RULE_createStmt = 22, 
		RULE_createContext = 23, RULE_createScalar = 24, RULE_createEnum = 25, 
		RULE_enumEntry = 26, RULE_createStruct = 27, RULE_createUnion = 28, RULE_unionAlt = 29, 
		RULE_fieldDef = 30, RULE_jsonString = 31, RULE_typeName = 32, RULE_createStream = 33, 
		RULE_streamTypeDef = 34, RULE_inlineStruct = 35, RULE_typeAlias = 36, 
		RULE_dataType = 37, RULE_primitiveType = 38, RULE_compositeType = 39, 
		RULE_complexType = 40, RULE_booleanExpr = 41, RULE_orExpr = 42, RULE_andExpr = 43, 
		RULE_notExpr = 44, RULE_predicate = 45, RULE_value = 46, RULE_columnName = 47, 
		RULE_literal = 48, RULE_cmpOp = 49, RULE_qname = 50, RULE_identifier = 51;
	private static String[] makeRuleNames() {
		return new String[] {
			"script", "includeSection", "includePragma", "filePath", "statement", 
			"useStmt", "useContext", "dmlStmt", "readStmt", "streamName", "typeBlock", 
			"projection", "projectionItem", "whereClause", "writeStmt", "fieldPathList", 
			"fieldPath", "pathSeg", "tuple", "literalOnlyList", "valueLit", "ddlStmt", 
			"createStmt", "createContext", "createScalar", "createEnum", "enumEntry", 
			"createStruct", "createUnion", "unionAlt", "fieldDef", "jsonString", 
			"typeName", "createStream", "streamTypeDef", "inlineStruct", "typeAlias", 
			"dataType", "primitiveType", "compositeType", "complexType", "booleanExpr", 
			"orExpr", "andExpr", "notExpr", "predicate", "value", "columnName", "literal", 
			"cmpOp", "qname", "identifier"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, "'*'", "','", "'.'", "':'", "'('", "')'", "'['", 
			"']'", "'<'", "'>'", "'='", null, "'<='", "'>='", "';'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "INCLUDE", "CREATE", "CONTEXT", "USE", "SCALAR", "ENUM", "STRUCT", 
			"UNION", "STREAM", "READ", "WRITE", "FROM", "TO", "LOG", "COMPACT", "TYPE", 
			"WHERE", "AS", "OPTIONAL", "VALUES", "OR", "AND", "IS", "NOT", "DEFAULT", 
			"TRUE", "FALSE", "NULL", "BOOL", "INT8", "UINT8", "INT16", "UINT16", 
			"INT32", "UINT32", "INT64", "UINT64", "SINGLE", "DOUBLE", "DECIMAL", 
			"STRING", "FSTRING", "BYTES", "FBYTES", "UUID", "DATE", "TIME", "TIMESTAMP", 
			"TIMESTAMP_TZ", "LIST", "MAP", "STAR", "COMMA", "DOT", "COLON", "LPAREN", 
			"RPAREN", "LBRACK", "RBRACK", "LT", "GT", "EQ", "NEQ", "LTE", "GTE", 
			"SEMI", "STRING_LIT", "NUMBER", "ID", "WS", "COMMENT", "WS_PATH", "FILE_PATH", 
			"BAD_PATH_CHAR"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "SqlStreamParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SqlStreamParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ScriptContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SqlStreamParser.EOF, 0); }
		public IncludeSectionContext includeSection() {
			return getRuleContext(IncludeSectionContext.class,0);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SqlStreamParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SqlStreamParser.SEMI, i);
		}
		public ScriptContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_script; }
	}

	public final ScriptContext script() throws RecognitionException {
		ScriptContext _localctx = new ScriptContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_script);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(105);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==INCLUDE) {
				{
				setState(104);
				includeSection();
				}
			}

			setState(110); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(107);
				statement();
				setState(108);
				match(SEMI);
				}
				}
				setState(112); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 3092L) != 0) );
			setState(114);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IncludeSectionContext extends ParserRuleContext {
		public List<IncludePragmaContext> includePragma() {
			return getRuleContexts(IncludePragmaContext.class);
		}
		public IncludePragmaContext includePragma(int i) {
			return getRuleContext(IncludePragmaContext.class,i);
		}
		public List<TerminalNode> SEMI() { return getTokens(SqlStreamParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(SqlStreamParser.SEMI, i);
		}
		public IncludeSectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_includeSection; }
	}

	public final IncludeSectionContext includeSection() throws RecognitionException {
		IncludeSectionContext _localctx = new IncludeSectionContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_includeSection);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(119); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(116);
				includePragma();
				setState(117);
				match(SEMI);
				}
				}
				setState(121); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==INCLUDE );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IncludePragmaContext extends ParserRuleContext {
		public TerminalNode INCLUDE() { return getToken(SqlStreamParser.INCLUDE, 0); }
		public FilePathContext filePath() {
			return getRuleContext(FilePathContext.class,0);
		}
		public IncludePragmaContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_includePragma; }
	}

	public final IncludePragmaContext includePragma() throws RecognitionException {
		IncludePragmaContext _localctx = new IncludePragmaContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_includePragma);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(123);
			match(INCLUDE);
			setState(124);
			filePath();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FilePathContext extends ParserRuleContext {
		public TerminalNode FILE_PATH() { return getToken(SqlStreamParser.FILE_PATH, 0); }
		public FilePathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_filePath; }
	}

	public final FilePathContext filePath() throws RecognitionException {
		FilePathContext _localctx = new FilePathContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_filePath);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			match(FILE_PATH);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public UseStmtContext useStmt() {
			return getRuleContext(UseStmtContext.class,0);
		}
		public DmlStmtContext dmlStmt() {
			return getRuleContext(DmlStmtContext.class,0);
		}
		public DdlStmtContext ddlStmt() {
			return getRuleContext(DdlStmtContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_statement);
		try {
			setState(131);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case USE:
				enterOuterAlt(_localctx, 1);
				{
				setState(128);
				useStmt();
				}
				break;
			case READ:
			case WRITE:
				enterOuterAlt(_localctx, 2);
				{
				setState(129);
				dmlStmt();
				}
				break;
			case CREATE:
				enterOuterAlt(_localctx, 3);
				{
				setState(130);
				ddlStmt();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UseStmtContext extends ParserRuleContext {
		public UseContextContext useContext() {
			return getRuleContext(UseContextContext.class,0);
		}
		public UseStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_useStmt; }
	}

	public final UseStmtContext useStmt() throws RecognitionException {
		UseStmtContext _localctx = new UseStmtContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_useStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(133);
			useContext();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UseContextContext extends ParserRuleContext {
		public TerminalNode USE() { return getToken(SqlStreamParser.USE, 0); }
		public TerminalNode CONTEXT() { return getToken(SqlStreamParser.CONTEXT, 0); }
		public QnameContext qname() {
			return getRuleContext(QnameContext.class,0);
		}
		public UseContextContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_useContext; }
	}

	public final UseContextContext useContext() throws RecognitionException {
		UseContextContext _localctx = new UseContextContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_useContext);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(135);
			match(USE);
			setState(136);
			match(CONTEXT);
			setState(137);
			qname();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DmlStmtContext extends ParserRuleContext {
		public ReadStmtContext readStmt() {
			return getRuleContext(ReadStmtContext.class,0);
		}
		public WriteStmtContext writeStmt() {
			return getRuleContext(WriteStmtContext.class,0);
		}
		public DmlStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dmlStmt; }
	}

	public final DmlStmtContext dmlStmt() throws RecognitionException {
		DmlStmtContext _localctx = new DmlStmtContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_dmlStmt);
		try {
			setState(141);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case READ:
				enterOuterAlt(_localctx, 1);
				{
				setState(139);
				readStmt();
				}
				break;
			case WRITE:
				enterOuterAlt(_localctx, 2);
				{
				setState(140);
				writeStmt();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ReadStmtContext extends ParserRuleContext {
		public TerminalNode READ() { return getToken(SqlStreamParser.READ, 0); }
		public TerminalNode FROM() { return getToken(SqlStreamParser.FROM, 0); }
		public StreamNameContext streamName() {
			return getRuleContext(StreamNameContext.class,0);
		}
		public List<TypeBlockContext> typeBlock() {
			return getRuleContexts(TypeBlockContext.class);
		}
		public TypeBlockContext typeBlock(int i) {
			return getRuleContext(TypeBlockContext.class,i);
		}
		public ReadStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_readStmt; }
	}

	public final ReadStmtContext readStmt() throws RecognitionException {
		ReadStmtContext _localctx = new ReadStmtContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_readStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(143);
			match(READ);
			setState(144);
			match(FROM);
			setState(145);
			streamName();
			setState(147); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(146);
				typeBlock();
				}
				}
				setState(149); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==TYPE );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StreamNameContext extends ParserRuleContext {
		public QnameContext qname() {
			return getRuleContext(QnameContext.class,0);
		}
		public StreamNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_streamName; }
	}

	public final StreamNameContext streamName() throws RecognitionException {
		StreamNameContext _localctx = new StreamNameContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_streamName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(151);
			qname();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeBlockContext extends ParserRuleContext {
		public TerminalNode TYPE() { return getToken(SqlStreamParser.TYPE, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public ProjectionContext projection() {
			return getRuleContext(ProjectionContext.class,0);
		}
		public WhereClauseContext whereClause() {
			return getRuleContext(WhereClauseContext.class,0);
		}
		public TypeBlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeBlock; }
	}

	public final TypeBlockContext typeBlock() throws RecognitionException {
		TypeBlockContext _localctx = new TypeBlockContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_typeBlock);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(153);
			match(TYPE);
			setState(154);
			typeName();
			setState(155);
			projection();
			setState(157);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==WHERE) {
				{
				setState(156);
				whereClause();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProjectionContext extends ParserRuleContext {
		public List<ProjectionItemContext> projectionItem() {
			return getRuleContexts(ProjectionItemContext.class);
		}
		public ProjectionItemContext projectionItem(int i) {
			return getRuleContext(ProjectionItemContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public ProjectionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_projection; }
	}

	public final ProjectionContext projection() throws RecognitionException {
		ProjectionContext _localctx = new ProjectionContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_projection);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(159);
			projectionItem();
			setState(164);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(160);
				match(COMMA);
				setState(161);
				projectionItem();
				}
				}
				setState(166);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProjectionItemContext extends ParserRuleContext {
		public ProjectionItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_projectionItem; }
	 
		public ProjectionItemContext() { }
		public void copyFrom(ProjectionItemContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ProjectColContext extends ProjectionItemContext {
		public ColumnNameContext columnName() {
			return getRuleContext(ColumnNameContext.class,0);
		}
		public ProjectColContext(ProjectionItemContext ctx) { copyFrom(ctx); }
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ProjectAllContext extends ProjectionItemContext {
		public TerminalNode STAR() { return getToken(SqlStreamParser.STAR, 0); }
		public ProjectAllContext(ProjectionItemContext ctx) { copyFrom(ctx); }
	}

	public final ProjectionItemContext projectionItem() throws RecognitionException {
		ProjectionItemContext _localctx = new ProjectionItemContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_projectionItem);
		try {
			setState(169);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STAR:
				_localctx = new ProjectAllContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(167);
				match(STAR);
				}
				break;
			case ID:
				_localctx = new ProjectColContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(168);
				columnName();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WhereClauseContext extends ParserRuleContext {
		public TerminalNode WHERE() { return getToken(SqlStreamParser.WHERE, 0); }
		public BooleanExprContext booleanExpr() {
			return getRuleContext(BooleanExprContext.class,0);
		}
		public WhereClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whereClause; }
	}

	public final WhereClauseContext whereClause() throws RecognitionException {
		WhereClauseContext _localctx = new WhereClauseContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_whereClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(171);
			match(WHERE);
			setState(172);
			booleanExpr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WriteStmtContext extends ParserRuleContext {
		public TerminalNode WRITE() { return getToken(SqlStreamParser.WRITE, 0); }
		public TerminalNode TO() { return getToken(SqlStreamParser.TO, 0); }
		public StreamNameContext streamName() {
			return getRuleContext(StreamNameContext.class,0);
		}
		public TerminalNode TYPE() { return getToken(SqlStreamParser.TYPE, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public FieldPathListContext fieldPathList() {
			return getRuleContext(FieldPathListContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public TerminalNode VALUES() { return getToken(SqlStreamParser.VALUES, 0); }
		public List<TupleContext> tuple() {
			return getRuleContexts(TupleContext.class);
		}
		public TupleContext tuple(int i) {
			return getRuleContext(TupleContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public WriteStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_writeStmt; }
	}

	public final WriteStmtContext writeStmt() throws RecognitionException {
		WriteStmtContext _localctx = new WriteStmtContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_writeStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(174);
			match(WRITE);
			setState(175);
			match(TO);
			setState(176);
			streamName();
			setState(177);
			match(TYPE);
			setState(178);
			typeName();
			setState(179);
			match(LPAREN);
			setState(180);
			fieldPathList();
			setState(181);
			match(RPAREN);
			setState(182);
			match(VALUES);
			setState(183);
			tuple();
			setState(188);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(184);
				match(COMMA);
				setState(185);
				tuple();
				}
				}
				setState(190);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldPathListContext extends ParserRuleContext {
		public List<FieldPathContext> fieldPath() {
			return getRuleContexts(FieldPathContext.class);
		}
		public FieldPathContext fieldPath(int i) {
			return getRuleContext(FieldPathContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public FieldPathListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldPathList; }
	}

	public final FieldPathListContext fieldPathList() throws RecognitionException {
		FieldPathListContext _localctx = new FieldPathListContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_fieldPathList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(191);
			fieldPath();
			setState(196);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(192);
				match(COMMA);
				setState(193);
				fieldPath();
				}
				}
				setState(198);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldPathContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<PathSegContext> pathSeg() {
			return getRuleContexts(PathSegContext.class);
		}
		public PathSegContext pathSeg(int i) {
			return getRuleContext(PathSegContext.class,i);
		}
		public FieldPathContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldPath; }
	}

	public final FieldPathContext fieldPath() throws RecognitionException {
		FieldPathContext _localctx = new FieldPathContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_fieldPath);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(199);
			identifier();
			setState(203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT || _la==LBRACK) {
				{
				{
				setState(200);
				pathSeg();
				}
				}
				setState(205);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PathSegContext extends ParserRuleContext {
		public TerminalNode DOT() { return getToken(SqlStreamParser.DOT, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode LBRACK() { return getToken(SqlStreamParser.LBRACK, 0); }
		public TerminalNode NUMBER() { return getToken(SqlStreamParser.NUMBER, 0); }
		public TerminalNode RBRACK() { return getToken(SqlStreamParser.RBRACK, 0); }
		public TerminalNode STRING_LIT() { return getToken(SqlStreamParser.STRING_LIT, 0); }
		public PathSegContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pathSeg; }
	}

	public final PathSegContext pathSeg() throws RecognitionException {
		PathSegContext _localctx = new PathSegContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_pathSeg);
		try {
			setState(214);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(206);
				match(DOT);
				setState(207);
				identifier();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(208);
				match(LBRACK);
				setState(209);
				match(NUMBER);
				setState(210);
				match(RBRACK);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(211);
				match(LBRACK);
				setState(212);
				match(STRING_LIT);
				setState(213);
				match(RBRACK);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TupleContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public LiteralOnlyListContext literalOnlyList() {
			return getRuleContext(LiteralOnlyListContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public TupleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tuple; }
	}

	public final TupleContext tuple() throws RecognitionException {
		TupleContext _localctx = new TupleContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_tuple);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(216);
			match(LPAREN);
			setState(217);
			literalOnlyList();
			setState(218);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralOnlyListContext extends ParserRuleContext {
		public List<ValueLitContext> valueLit() {
			return getRuleContexts(ValueLitContext.class);
		}
		public ValueLitContext valueLit(int i) {
			return getRuleContext(ValueLitContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public LiteralOnlyListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literalOnlyList; }
	}

	public final LiteralOnlyListContext literalOnlyList() throws RecognitionException {
		LiteralOnlyListContext _localctx = new LiteralOnlyListContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_literalOnlyList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(220);
			valueLit();
			setState(225);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(221);
				match(COMMA);
				setState(222);
				valueLit();
				}
				}
				setState(227);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueLitContext extends ParserRuleContext {
		public TerminalNode STRING_LIT() { return getToken(SqlStreamParser.STRING_LIT, 0); }
		public TerminalNode NUMBER() { return getToken(SqlStreamParser.NUMBER, 0); }
		public TerminalNode TRUE() { return getToken(SqlStreamParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(SqlStreamParser.FALSE, 0); }
		public TerminalNode NULL() { return getToken(SqlStreamParser.NULL, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ValueLitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valueLit; }
	}

	public final ValueLitContext valueLit() throws RecognitionException {
		ValueLitContext _localctx = new ValueLitContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_valueLit);
		try {
			setState(234);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING_LIT:
				enterOuterAlt(_localctx, 1);
				{
				setState(228);
				match(STRING_LIT);
				}
				break;
			case NUMBER:
				enterOuterAlt(_localctx, 2);
				{
				setState(229);
				match(NUMBER);
				}
				break;
			case TRUE:
				enterOuterAlt(_localctx, 3);
				{
				setState(230);
				match(TRUE);
				}
				break;
			case FALSE:
				enterOuterAlt(_localctx, 4);
				{
				setState(231);
				match(FALSE);
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 5);
				{
				setState(232);
				match(NULL);
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 6);
				{
				setState(233);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DdlStmtContext extends ParserRuleContext {
		public CreateStmtContext createStmt() {
			return getRuleContext(CreateStmtContext.class,0);
		}
		public DdlStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ddlStmt; }
	}

	public final DdlStmtContext ddlStmt() throws RecognitionException {
		DdlStmtContext _localctx = new DdlStmtContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_ddlStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(236);
			createStmt();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateStmtContext extends ParserRuleContext {
		public CreateContextContext createContext() {
			return getRuleContext(CreateContextContext.class,0);
		}
		public CreateScalarContext createScalar() {
			return getRuleContext(CreateScalarContext.class,0);
		}
		public CreateEnumContext createEnum() {
			return getRuleContext(CreateEnumContext.class,0);
		}
		public CreateStructContext createStruct() {
			return getRuleContext(CreateStructContext.class,0);
		}
		public CreateUnionContext createUnion() {
			return getRuleContext(CreateUnionContext.class,0);
		}
		public CreateStreamContext createStream() {
			return getRuleContext(CreateStreamContext.class,0);
		}
		public CreateStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createStmt; }
	}

	public final CreateStmtContext createStmt() throws RecognitionException {
		CreateStmtContext _localctx = new CreateStmtContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_createStmt);
		try {
			setState(244);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(238);
				createContext();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(239);
				createScalar();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(240);
				createEnum();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(241);
				createStruct();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(242);
				createUnion();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(243);
				createStream();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateContextContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode CONTEXT() { return getToken(SqlStreamParser.CONTEXT, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public CreateContextContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createContext; }
	}

	public final CreateContextContext createContext() throws RecognitionException {
		CreateContextContext _localctx = new CreateContextContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_createContext);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(246);
			match(CREATE);
			setState(247);
			match(CONTEXT);
			setState(248);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateScalarContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode SCALAR() { return getToken(SqlStreamParser.SCALAR, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode AS() { return getToken(SqlStreamParser.AS, 0); }
		public PrimitiveTypeContext primitiveType() {
			return getRuleContext(PrimitiveTypeContext.class,0);
		}
		public CreateScalarContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createScalar; }
	}

	public final CreateScalarContext createScalar() throws RecognitionException {
		CreateScalarContext _localctx = new CreateScalarContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_createScalar);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(250);
			match(CREATE);
			setState(251);
			match(SCALAR);
			setState(252);
			typeName();
			setState(253);
			match(AS);
			setState(254);
			primitiveType();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateEnumContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode ENUM() { return getToken(SqlStreamParser.ENUM, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public List<EnumEntryContext> enumEntry() {
			return getRuleContexts(EnumEntryContext.class);
		}
		public EnumEntryContext enumEntry(int i) {
			return getRuleContext(EnumEntryContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public CreateEnumContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createEnum; }
	}

	public final CreateEnumContext createEnum() throws RecognitionException {
		CreateEnumContext _localctx = new CreateEnumContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_createEnum);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(256);
			match(CREATE);
			setState(257);
			match(ENUM);
			setState(258);
			typeName();
			setState(259);
			match(LPAREN);
			setState(260);
			enumEntry();
			setState(265);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(261);
				match(COMMA);
				setState(262);
				enumEntry();
				}
				}
				setState(267);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(268);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumEntryContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode EQ() { return getToken(SqlStreamParser.EQ, 0); }
		public TerminalNode NUMBER() { return getToken(SqlStreamParser.NUMBER, 0); }
		public EnumEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumEntry; }
	}

	public final EnumEntryContext enumEntry() throws RecognitionException {
		EnumEntryContext _localctx = new EnumEntryContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_enumEntry);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(270);
			identifier();
			setState(271);
			match(EQ);
			setState(272);
			match(NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateStructContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode STRUCT() { return getToken(SqlStreamParser.STRUCT, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public List<FieldDefContext> fieldDef() {
			return getRuleContexts(FieldDefContext.class);
		}
		public FieldDefContext fieldDef(int i) {
			return getRuleContext(FieldDefContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public CreateStructContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createStruct; }
	}

	public final CreateStructContext createStruct() throws RecognitionException {
		CreateStructContext _localctx = new CreateStructContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_createStruct);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(274);
			match(CREATE);
			setState(275);
			match(STRUCT);
			setState(276);
			typeName();
			setState(277);
			match(LPAREN);
			setState(278);
			fieldDef();
			setState(283);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(279);
				match(COMMA);
				setState(280);
				fieldDef();
				}
				}
				setState(285);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(286);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateUnionContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode UNION() { return getToken(SqlStreamParser.UNION, 0); }
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public List<UnionAltContext> unionAlt() {
			return getRuleContexts(UnionAltContext.class);
		}
		public UnionAltContext unionAlt(int i) {
			return getRuleContext(UnionAltContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public CreateUnionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createUnion; }
	}

	public final CreateUnionContext createUnion() throws RecognitionException {
		CreateUnionContext _localctx = new CreateUnionContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_createUnion);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
			match(CREATE);
			setState(289);
			match(UNION);
			setState(290);
			typeName();
			setState(291);
			match(LPAREN);
			setState(292);
			unionAlt();
			setState(297);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(293);
				match(COMMA);
				setState(294);
				unionAlt();
				}
				}
				setState(299);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(300);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionAltContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode COLON() { return getToken(SqlStreamParser.COLON, 0); }
		public DataTypeContext dataType() {
			return getRuleContext(DataTypeContext.class,0);
		}
		public UnionAltContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionAlt; }
	}

	public final UnionAltContext unionAlt() throws RecognitionException {
		UnionAltContext _localctx = new UnionAltContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_unionAlt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(302);
			identifier();
			setState(303);
			match(COLON);
			setState(304);
			dataType();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldDefContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public DataTypeContext dataType() {
			return getRuleContext(DataTypeContext.class,0);
		}
		public TerminalNode OPTIONAL() { return getToken(SqlStreamParser.OPTIONAL, 0); }
		public TerminalNode DEFAULT() { return getToken(SqlStreamParser.DEFAULT, 0); }
		public JsonStringContext jsonString() {
			return getRuleContext(JsonStringContext.class,0);
		}
		public FieldDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldDef; }
	}

	public final FieldDefContext fieldDef() throws RecognitionException {
		FieldDefContext _localctx = new FieldDefContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_fieldDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(306);
			identifier();
			setState(307);
			dataType();
			setState(309);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPTIONAL) {
				{
				setState(308);
				match(OPTIONAL);
				}
			}

			setState(313);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DEFAULT) {
				{
				setState(311);
				match(DEFAULT);
				setState(312);
				jsonString();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class JsonStringContext extends ParserRuleContext {
		public TerminalNode STRING_LIT() { return getToken(SqlStreamParser.STRING_LIT, 0); }
		public JsonStringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_jsonString; }
	}

	public final JsonStringContext jsonString() throws RecognitionException {
		JsonStringContext _localctx = new JsonStringContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_jsonString);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(315);
			match(STRING_LIT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeNameContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeName; }
	}

	public final TypeNameContext typeName() throws RecognitionException {
		TypeNameContext _localctx = new TypeNameContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_typeName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(317);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CreateStreamContext extends ParserRuleContext {
		public TerminalNode CREATE() { return getToken(SqlStreamParser.CREATE, 0); }
		public TerminalNode STREAM() { return getToken(SqlStreamParser.STREAM, 0); }
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode AS() { return getToken(SqlStreamParser.AS, 0); }
		public TerminalNode LOG() { return getToken(SqlStreamParser.LOG, 0); }
		public TerminalNode COMPACT() { return getToken(SqlStreamParser.COMPACT, 0); }
		public List<StreamTypeDefContext> streamTypeDef() {
			return getRuleContexts(StreamTypeDefContext.class);
		}
		public StreamTypeDefContext streamTypeDef(int i) {
			return getRuleContext(StreamTypeDefContext.class,i);
		}
		public CreateStreamContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_createStream; }
	}

	public final CreateStreamContext createStream() throws RecognitionException {
		CreateStreamContext _localctx = new CreateStreamContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_createStream);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(319);
			match(CREATE);
			setState(320);
			_la = _input.LA(1);
			if ( !(_la==LOG || _la==COMPACT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(321);
			match(STREAM);
			setState(322);
			identifier();
			setState(323);
			match(AS);
			setState(325); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(324);
				streamTypeDef();
				}
				}
				setState(327); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==TYPE );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StreamTypeDefContext extends ParserRuleContext {
		public TerminalNode TYPE() { return getToken(SqlStreamParser.TYPE, 0); }
		public TerminalNode AS() { return getToken(SqlStreamParser.AS, 0); }
		public TypeAliasContext typeAlias() {
			return getRuleContext(TypeAliasContext.class,0);
		}
		public InlineStructContext inlineStruct() {
			return getRuleContext(InlineStructContext.class,0);
		}
		public QnameContext qname() {
			return getRuleContext(QnameContext.class,0);
		}
		public StreamTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_streamTypeDef; }
	}

	public final StreamTypeDefContext streamTypeDef() throws RecognitionException {
		StreamTypeDefContext _localctx = new StreamTypeDefContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_streamTypeDef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(329);
			match(TYPE);
			setState(332);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LPAREN:
				{
				setState(330);
				inlineStruct();
				}
				break;
			case DOT:
			case ID:
				{
				setState(331);
				qname();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(334);
			match(AS);
			setState(335);
			typeAlias();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InlineStructContext extends ParserRuleContext {
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public List<FieldDefContext> fieldDef() {
			return getRuleContexts(FieldDefContext.class);
		}
		public FieldDefContext fieldDef(int i) {
			return getRuleContext(FieldDefContext.class,i);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SqlStreamParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SqlStreamParser.COMMA, i);
		}
		public InlineStructContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inlineStruct; }
	}

	public final InlineStructContext inlineStruct() throws RecognitionException {
		InlineStructContext _localctx = new InlineStructContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_inlineStruct);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(337);
			match(LPAREN);
			setState(338);
			fieldDef();
			setState(343);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(339);
				match(COMMA);
				setState(340);
				fieldDef();
				}
				}
				setState(345);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(346);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeAliasContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TypeAliasContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeAlias; }
	}

	public final TypeAliasContext typeAlias() throws RecognitionException {
		TypeAliasContext _localctx = new TypeAliasContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_typeAlias);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(348);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DataTypeContext extends ParserRuleContext {
		public PrimitiveTypeContext primitiveType() {
			return getRuleContext(PrimitiveTypeContext.class,0);
		}
		public CompositeTypeContext compositeType() {
			return getRuleContext(CompositeTypeContext.class,0);
		}
		public ComplexTypeContext complexType() {
			return getRuleContext(ComplexTypeContext.class,0);
		}
		public DataTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dataType; }
	}

	public final DataTypeContext dataType() throws RecognitionException {
		DataTypeContext _localctx = new DataTypeContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_dataType);
		try {
			setState(353);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BOOL:
			case INT8:
			case UINT8:
			case INT16:
			case UINT16:
			case INT32:
			case UINT32:
			case INT64:
			case UINT64:
			case SINGLE:
			case DOUBLE:
			case DECIMAL:
			case STRING:
			case FSTRING:
			case BYTES:
			case FBYTES:
			case UUID:
			case DATE:
			case TIME:
			case TIMESTAMP:
			case TIMESTAMP_TZ:
				enterOuterAlt(_localctx, 1);
				{
				setState(350);
				primitiveType();
				}
				break;
			case LIST:
			case MAP:
				enterOuterAlt(_localctx, 2);
				{
				setState(351);
				compositeType();
				}
				break;
			case DOT:
			case ID:
				enterOuterAlt(_localctx, 3);
				{
				setState(352);
				complexType();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PrimitiveTypeContext extends ParserRuleContext {
		public TerminalNode BOOL() { return getToken(SqlStreamParser.BOOL, 0); }
		public TerminalNode INT8() { return getToken(SqlStreamParser.INT8, 0); }
		public TerminalNode UINT8() { return getToken(SqlStreamParser.UINT8, 0); }
		public TerminalNode INT16() { return getToken(SqlStreamParser.INT16, 0); }
		public TerminalNode UINT16() { return getToken(SqlStreamParser.UINT16, 0); }
		public TerminalNode INT32() { return getToken(SqlStreamParser.INT32, 0); }
		public TerminalNode UINT32() { return getToken(SqlStreamParser.UINT32, 0); }
		public TerminalNode INT64() { return getToken(SqlStreamParser.INT64, 0); }
		public TerminalNode UINT64() { return getToken(SqlStreamParser.UINT64, 0); }
		public TerminalNode SINGLE() { return getToken(SqlStreamParser.SINGLE, 0); }
		public TerminalNode DOUBLE() { return getToken(SqlStreamParser.DOUBLE, 0); }
		public TerminalNode STRING() { return getToken(SqlStreamParser.STRING, 0); }
		public TerminalNode FSTRING() { return getToken(SqlStreamParser.FSTRING, 0); }
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public List<TerminalNode> NUMBER() { return getTokens(SqlStreamParser.NUMBER); }
		public TerminalNode NUMBER(int i) {
			return getToken(SqlStreamParser.NUMBER, i);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public TerminalNode BYTES() { return getToken(SqlStreamParser.BYTES, 0); }
		public TerminalNode FBYTES() { return getToken(SqlStreamParser.FBYTES, 0); }
		public TerminalNode UUID() { return getToken(SqlStreamParser.UUID, 0); }
		public TerminalNode DATE() { return getToken(SqlStreamParser.DATE, 0); }
		public TerminalNode TIME() { return getToken(SqlStreamParser.TIME, 0); }
		public TerminalNode TIMESTAMP() { return getToken(SqlStreamParser.TIMESTAMP, 0); }
		public TerminalNode TIMESTAMP_TZ() { return getToken(SqlStreamParser.TIMESTAMP_TZ, 0); }
		public TerminalNode DECIMAL() { return getToken(SqlStreamParser.DECIMAL, 0); }
		public TerminalNode COMMA() { return getToken(SqlStreamParser.COMMA, 0); }
		public PrimitiveTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primitiveType; }
	}

	public final PrimitiveTypeContext primitiveType() throws RecognitionException {
		PrimitiveTypeContext _localctx = new PrimitiveTypeContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_primitiveType);
		try {
			setState(396);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BOOL:
				enterOuterAlt(_localctx, 1);
				{
				setState(355);
				match(BOOL);
				}
				break;
			case INT8:
				enterOuterAlt(_localctx, 2);
				{
				setState(356);
				match(INT8);
				}
				break;
			case UINT8:
				enterOuterAlt(_localctx, 3);
				{
				setState(357);
				match(UINT8);
				}
				break;
			case INT16:
				enterOuterAlt(_localctx, 4);
				{
				setState(358);
				match(INT16);
				}
				break;
			case UINT16:
				enterOuterAlt(_localctx, 5);
				{
				setState(359);
				match(UINT16);
				}
				break;
			case INT32:
				enterOuterAlt(_localctx, 6);
				{
				setState(360);
				match(INT32);
				}
				break;
			case UINT32:
				enterOuterAlt(_localctx, 7);
				{
				setState(361);
				match(UINT32);
				}
				break;
			case INT64:
				enterOuterAlt(_localctx, 8);
				{
				setState(362);
				match(INT64);
				}
				break;
			case UINT64:
				enterOuterAlt(_localctx, 9);
				{
				setState(363);
				match(UINT64);
				}
				break;
			case SINGLE:
				enterOuterAlt(_localctx, 10);
				{
				setState(364);
				match(SINGLE);
				}
				break;
			case DOUBLE:
				enterOuterAlt(_localctx, 11);
				{
				setState(365);
				match(DOUBLE);
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 12);
				{
				setState(366);
				match(STRING);
				}
				break;
			case FSTRING:
				enterOuterAlt(_localctx, 13);
				{
				setState(367);
				match(FSTRING);
				setState(368);
				match(LPAREN);
				setState(369);
				match(NUMBER);
				setState(370);
				match(RPAREN);
				}
				break;
			case BYTES:
				enterOuterAlt(_localctx, 14);
				{
				setState(371);
				match(BYTES);
				}
				break;
			case FBYTES:
				enterOuterAlt(_localctx, 15);
				{
				setState(372);
				match(FBYTES);
				setState(373);
				match(LPAREN);
				setState(374);
				match(NUMBER);
				setState(375);
				match(RPAREN);
				}
				break;
			case UUID:
				enterOuterAlt(_localctx, 16);
				{
				setState(376);
				match(UUID);
				}
				break;
			case DATE:
				enterOuterAlt(_localctx, 17);
				{
				setState(377);
				match(DATE);
				}
				break;
			case TIME:
				enterOuterAlt(_localctx, 18);
				{
				setState(378);
				match(TIME);
				setState(379);
				match(LPAREN);
				setState(380);
				match(NUMBER);
				setState(381);
				match(RPAREN);
				}
				break;
			case TIMESTAMP:
				enterOuterAlt(_localctx, 19);
				{
				setState(382);
				match(TIMESTAMP);
				setState(383);
				match(LPAREN);
				setState(384);
				match(NUMBER);
				setState(385);
				match(RPAREN);
				}
				break;
			case TIMESTAMP_TZ:
				enterOuterAlt(_localctx, 20);
				{
				setState(386);
				match(TIMESTAMP_TZ);
				setState(387);
				match(LPAREN);
				setState(388);
				match(NUMBER);
				setState(389);
				match(RPAREN);
				}
				break;
			case DECIMAL:
				enterOuterAlt(_localctx, 21);
				{
				setState(390);
				match(DECIMAL);
				setState(391);
				match(LPAREN);
				setState(392);
				match(NUMBER);
				setState(393);
				match(COMMA);
				setState(394);
				match(NUMBER);
				setState(395);
				match(RPAREN);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompositeTypeContext extends ParserRuleContext {
		public TerminalNode LIST() { return getToken(SqlStreamParser.LIST, 0); }
		public TerminalNode LT() { return getToken(SqlStreamParser.LT, 0); }
		public DataTypeContext dataType() {
			return getRuleContext(DataTypeContext.class,0);
		}
		public TerminalNode GT() { return getToken(SqlStreamParser.GT, 0); }
		public TerminalNode MAP() { return getToken(SqlStreamParser.MAP, 0); }
		public PrimitiveTypeContext primitiveType() {
			return getRuleContext(PrimitiveTypeContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(SqlStreamParser.COMMA, 0); }
		public CompositeTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_compositeType; }
	}

	public final CompositeTypeContext compositeType() throws RecognitionException {
		CompositeTypeContext _localctx = new CompositeTypeContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_compositeType);
		try {
			setState(410);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LIST:
				enterOuterAlt(_localctx, 1);
				{
				setState(398);
				match(LIST);
				setState(399);
				match(LT);
				setState(400);
				dataType();
				setState(401);
				match(GT);
				}
				break;
			case MAP:
				enterOuterAlt(_localctx, 2);
				{
				setState(403);
				match(MAP);
				setState(404);
				match(LT);
				setState(405);
				primitiveType();
				setState(406);
				match(COMMA);
				setState(407);
				dataType();
				setState(408);
				match(GT);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ComplexTypeContext extends ParserRuleContext {
		public QnameContext qname() {
			return getRuleContext(QnameContext.class,0);
		}
		public ComplexTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_complexType; }
	}

	public final ComplexTypeContext complexType() throws RecognitionException {
		ComplexTypeContext _localctx = new ComplexTypeContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_complexType);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(412);
			qname();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanExprContext extends ParserRuleContext {
		public OrExprContext orExpr() {
			return getRuleContext(OrExprContext.class,0);
		}
		public BooleanExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanExpr; }
	}

	public final BooleanExprContext booleanExpr() throws RecognitionException {
		BooleanExprContext _localctx = new BooleanExprContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_booleanExpr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(414);
			orExpr();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OrExprContext extends ParserRuleContext {
		public List<AndExprContext> andExpr() {
			return getRuleContexts(AndExprContext.class);
		}
		public AndExprContext andExpr(int i) {
			return getRuleContext(AndExprContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(SqlStreamParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(SqlStreamParser.OR, i);
		}
		public OrExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_orExpr; }
	}

	public final OrExprContext orExpr() throws RecognitionException {
		OrExprContext _localctx = new OrExprContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_orExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(416);
			andExpr();
			setState(421);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR) {
				{
				{
				setState(417);
				match(OR);
				setState(418);
				andExpr();
				}
				}
				setState(423);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AndExprContext extends ParserRuleContext {
		public List<NotExprContext> notExpr() {
			return getRuleContexts(NotExprContext.class);
		}
		public NotExprContext notExpr(int i) {
			return getRuleContext(NotExprContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(SqlStreamParser.AND); }
		public TerminalNode AND(int i) {
			return getToken(SqlStreamParser.AND, i);
		}
		public AndExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_andExpr; }
	}

	public final AndExprContext andExpr() throws RecognitionException {
		AndExprContext _localctx = new AndExprContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_andExpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(424);
			notExpr();
			setState(429);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND) {
				{
				{
				setState(425);
				match(AND);
				setState(426);
				notExpr();
				}
				}
				setState(431);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NotExprContext extends ParserRuleContext {
		public TerminalNode NOT() { return getToken(SqlStreamParser.NOT, 0); }
		public NotExprContext notExpr() {
			return getRuleContext(NotExprContext.class,0);
		}
		public PredicateContext predicate() {
			return getRuleContext(PredicateContext.class,0);
		}
		public NotExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_notExpr; }
	}

	public final NotExprContext notExpr() throws RecognitionException {
		NotExprContext _localctx = new NotExprContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_notExpr);
		try {
			setState(435);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(432);
				match(NOT);
				setState(433);
				notExpr();
				}
				break;
			case TRUE:
			case FALSE:
			case NULL:
			case LPAREN:
			case STRING_LIT:
			case NUMBER:
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(434);
				predicate();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PredicateContext extends ParserRuleContext {
		public PredicateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_predicate; }
	 
		public PredicateContext() { }
		public void copyFrom(PredicateContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IsNotNullPredicateContext extends PredicateContext {
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public TerminalNode IS() { return getToken(SqlStreamParser.IS, 0); }
		public TerminalNode NOT() { return getToken(SqlStreamParser.NOT, 0); }
		public TerminalNode NULL() { return getToken(SqlStreamParser.NULL, 0); }
		public IsNotNullPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ParenPredicateContext extends PredicateContext {
		public TerminalNode LPAREN() { return getToken(SqlStreamParser.LPAREN, 0); }
		public BooleanExprContext booleanExpr() {
			return getRuleContext(BooleanExprContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SqlStreamParser.RPAREN, 0); }
		public ParenPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IsNullPredicateContext extends PredicateContext {
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public TerminalNode IS() { return getToken(SqlStreamParser.IS, 0); }
		public TerminalNode NULL() { return getToken(SqlStreamParser.NULL, 0); }
		public IsNullPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
	}
	@SuppressWarnings("CheckReturnValue")
	public static class CmpPredicateContext extends PredicateContext {
		public List<ValueContext> value() {
			return getRuleContexts(ValueContext.class);
		}
		public ValueContext value(int i) {
			return getRuleContext(ValueContext.class,i);
		}
		public CmpOpContext cmpOp() {
			return getRuleContext(CmpOpContext.class,0);
		}
		public CmpPredicateContext(PredicateContext ctx) { copyFrom(ctx); }
	}

	public final PredicateContext predicate() throws RecognitionException {
		PredicateContext _localctx = new PredicateContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_predicate);
		try {
			setState(454);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,30,_ctx) ) {
			case 1:
				_localctx = new CmpPredicateContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(437);
				value();
				setState(438);
				cmpOp();
				setState(439);
				value();
				}
				break;
			case 2:
				_localctx = new IsNullPredicateContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(441);
				value();
				setState(442);
				match(IS);
				setState(443);
				match(NULL);
				}
				break;
			case 3:
				_localctx = new IsNotNullPredicateContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(445);
				value();
				setState(446);
				match(IS);
				setState(447);
				match(NOT);
				setState(448);
				match(NULL);
				}
				break;
			case 4:
				_localctx = new ParenPredicateContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(450);
				match(LPAREN);
				setState(451);
				booleanExpr();
				setState(452);
				match(RPAREN);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends ParserRuleContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public ColumnNameContext columnName() {
			return getRuleContext(ColumnNameContext.class,0);
		}
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_value);
		try {
			setState(458);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TRUE:
			case FALSE:
			case NULL:
			case STRING_LIT:
			case NUMBER:
				enterOuterAlt(_localctx, 1);
				{
				setState(456);
				literal();
				}
				break;
			case ID:
				enterOuterAlt(_localctx, 2);
				{
				setState(457);
				columnName();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ColumnNameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(SqlStreamParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(SqlStreamParser.DOT, i);
		}
		public ColumnNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_columnName; }
	}

	public final ColumnNameContext columnName() throws RecognitionException {
		ColumnNameContext _localctx = new ColumnNameContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_columnName);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(460);
			identifier();
			setState(465);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(461);
				match(DOT);
				setState(462);
				identifier();
				}
				}
				setState(467);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode STRING_LIT() { return getToken(SqlStreamParser.STRING_LIT, 0); }
		public TerminalNode NUMBER() { return getToken(SqlStreamParser.NUMBER, 0); }
		public TerminalNode TRUE() { return getToken(SqlStreamParser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(SqlStreamParser.FALSE, 0); }
		public TerminalNode NULL() { return getToken(SqlStreamParser.NULL, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(468);
			_la = _input.LA(1);
			if ( !(((((_la - 26)) & ~0x3f) == 0 && ((1L << (_la - 26)) & 6597069766663L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CmpOpContext extends ParserRuleContext {
		public TerminalNode EQ() { return getToken(SqlStreamParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(SqlStreamParser.NEQ, 0); }
		public TerminalNode LT() { return getToken(SqlStreamParser.LT, 0); }
		public TerminalNode LTE() { return getToken(SqlStreamParser.LTE, 0); }
		public TerminalNode GT() { return getToken(SqlStreamParser.GT, 0); }
		public TerminalNode GTE() { return getToken(SqlStreamParser.GTE, 0); }
		public CmpOpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cmpOp; }
	}

	public final CmpOpContext cmpOp() throws RecognitionException {
		CmpOpContext _localctx = new CmpOpContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_cmpOp);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(470);
			_la = _input.LA(1);
			if ( !(((((_la - 60)) & ~0x3f) == 0 && ((1L << (_la - 60)) & 63L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QnameContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(SqlStreamParser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(SqlStreamParser.DOT, i);
		}
		public QnameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qname; }
	}

	public final QnameContext qname() throws RecognitionException {
		QnameContext _localctx = new QnameContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_qname);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(473);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DOT) {
				{
				setState(472);
				match(DOT);
				}
			}

			setState(475);
			identifier();
			setState(480);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(476);
				match(DOT);
				setState(477);
				identifier();
				}
				}
				setState(482);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(SqlStreamParser.ID, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(483);
			match(ID);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001J\u01e6\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u0002"+
		"2\u00072\u00023\u00073\u0001\u0000\u0003\u0000j\b\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0004\u0000o\b\u0000\u000b\u0000\f\u0000p\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0004\u0001x\b\u0001"+
		"\u000b\u0001\f\u0001y\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004\u0084\b\u0004"+
		"\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0003\u0007\u008e\b\u0007\u0001\b\u0001\b\u0001"+
		"\b\u0001\b\u0004\b\u0094\b\b\u000b\b\f\b\u0095\u0001\t\u0001\t\u0001\n"+
		"\u0001\n\u0001\n\u0001\n\u0003\n\u009e\b\n\u0001\u000b\u0001\u000b\u0001"+
		"\u000b\u0005\u000b\u00a3\b\u000b\n\u000b\f\u000b\u00a6\t\u000b\u0001\f"+
		"\u0001\f\u0003\f\u00aa\b\f\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u00bb\b\u000e"+
		"\n\u000e\f\u000e\u00be\t\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0005"+
		"\u000f\u00c3\b\u000f\n\u000f\f\u000f\u00c6\t\u000f\u0001\u0010\u0001\u0010"+
		"\u0005\u0010\u00ca\b\u0010\n\u0010\f\u0010\u00cd\t\u0010\u0001\u0011\u0001"+
		"\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0003\u0011\u00d7\b\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0001"+
		"\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013\u00e0\b\u0013\n"+
		"\u0013\f\u0013\u00e3\t\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0001"+
		"\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u00eb\b\u0014\u0001\u0015\u0001"+
		"\u0015\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0001"+
		"\u0016\u0003\u0016\u00f5\b\u0016\u0001\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001"+
		"\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u0019\u0005\u0019\u0108\b\u0019\n\u0019\f\u0019\u010b\t\u0019"+
		"\u0001\u0019\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001a"+
		"\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0005\u001b\u011a\b\u001b\n\u001b\f\u001b\u011d\t\u001b\u0001"+
		"\u001b\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001"+
		"\u001c\u0001\u001c\u0001\u001c\u0005\u001c\u0128\b\u001c\n\u001c\f\u001c"+
		"\u012b\t\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0003\u001e\u0136\b\u001e"+
		"\u0001\u001e\u0001\u001e\u0003\u001e\u013a\b\u001e\u0001\u001f\u0001\u001f"+
		"\u0001 \u0001 \u0001!\u0001!\u0001!\u0001!\u0001!\u0001!\u0004!\u0146"+
		"\b!\u000b!\f!\u0147\u0001\"\u0001\"\u0001\"\u0003\"\u014d\b\"\u0001\""+
		"\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001#\u0005#\u0156\b#\n#\f#\u0159"+
		"\t#\u0001#\u0001#\u0001$\u0001$\u0001%\u0001%\u0001%\u0003%\u0162\b%\u0001"+
		"&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001"+
		"&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001"+
		"&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001"+
		"&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001&\u0001"+
		"&\u0003&\u018d\b&\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001"+
		"\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'\u0003\'\u019b\b\'\u0001(\u0001"+
		"(\u0001)\u0001)\u0001*\u0001*\u0001*\u0005*\u01a4\b*\n*\f*\u01a7\t*\u0001"+
		"+\u0001+\u0001+\u0005+\u01ac\b+\n+\f+\u01af\t+\u0001,\u0001,\u0001,\u0003"+
		",\u01b4\b,\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001"+
		"-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0001-\u0003-\u01c7"+
		"\b-\u0001.\u0001.\u0003.\u01cb\b.\u0001/\u0001/\u0001/\u0005/\u01d0\b"+
		"/\n/\f/\u01d3\t/\u00010\u00010\u00011\u00011\u00012\u00032\u01da\b2\u0001"+
		"2\u00012\u00012\u00052\u01df\b2\n2\f2\u01e2\t2\u00013\u00013\u00013\u0000"+
		"\u00004\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\^`bdf\u0000\u0003\u0001"+
		"\u0000\u000e\u000f\u0002\u0000\u001a\u001cCD\u0001\u0000<A\u01f4\u0000"+
		"i\u0001\u0000\u0000\u0000\u0002w\u0001\u0000\u0000\u0000\u0004{\u0001"+
		"\u0000\u0000\u0000\u0006~\u0001\u0000\u0000\u0000\b\u0083\u0001\u0000"+
		"\u0000\u0000\n\u0085\u0001\u0000\u0000\u0000\f\u0087\u0001\u0000\u0000"+
		"\u0000\u000e\u008d\u0001\u0000\u0000\u0000\u0010\u008f\u0001\u0000\u0000"+
		"\u0000\u0012\u0097\u0001\u0000\u0000\u0000\u0014\u0099\u0001\u0000\u0000"+
		"\u0000\u0016\u009f\u0001\u0000\u0000\u0000\u0018\u00a9\u0001\u0000\u0000"+
		"\u0000\u001a\u00ab\u0001\u0000\u0000\u0000\u001c\u00ae\u0001\u0000\u0000"+
		"\u0000\u001e\u00bf\u0001\u0000\u0000\u0000 \u00c7\u0001\u0000\u0000\u0000"+
		"\"\u00d6\u0001\u0000\u0000\u0000$\u00d8\u0001\u0000\u0000\u0000&\u00dc"+
		"\u0001\u0000\u0000\u0000(\u00ea\u0001\u0000\u0000\u0000*\u00ec\u0001\u0000"+
		"\u0000\u0000,\u00f4\u0001\u0000\u0000\u0000.\u00f6\u0001\u0000\u0000\u0000"+
		"0\u00fa\u0001\u0000\u0000\u00002\u0100\u0001\u0000\u0000\u00004\u010e"+
		"\u0001\u0000\u0000\u00006\u0112\u0001\u0000\u0000\u00008\u0120\u0001\u0000"+
		"\u0000\u0000:\u012e\u0001\u0000\u0000\u0000<\u0132\u0001\u0000\u0000\u0000"+
		">\u013b\u0001\u0000\u0000\u0000@\u013d\u0001\u0000\u0000\u0000B\u013f"+
		"\u0001\u0000\u0000\u0000D\u0149\u0001\u0000\u0000\u0000F\u0151\u0001\u0000"+
		"\u0000\u0000H\u015c\u0001\u0000\u0000\u0000J\u0161\u0001\u0000\u0000\u0000"+
		"L\u018c\u0001\u0000\u0000\u0000N\u019a\u0001\u0000\u0000\u0000P\u019c"+
		"\u0001\u0000\u0000\u0000R\u019e\u0001\u0000\u0000\u0000T\u01a0\u0001\u0000"+
		"\u0000\u0000V\u01a8\u0001\u0000\u0000\u0000X\u01b3\u0001\u0000\u0000\u0000"+
		"Z\u01c6\u0001\u0000\u0000\u0000\\\u01ca\u0001\u0000\u0000\u0000^\u01cc"+
		"\u0001\u0000\u0000\u0000`\u01d4\u0001\u0000\u0000\u0000b\u01d6\u0001\u0000"+
		"\u0000\u0000d\u01d9\u0001\u0000\u0000\u0000f\u01e3\u0001\u0000\u0000\u0000"+
		"hj\u0003\u0002\u0001\u0000ih\u0001\u0000\u0000\u0000ij\u0001\u0000\u0000"+
		"\u0000jn\u0001\u0000\u0000\u0000kl\u0003\b\u0004\u0000lm\u0005B\u0000"+
		"\u0000mo\u0001\u0000\u0000\u0000nk\u0001\u0000\u0000\u0000op\u0001\u0000"+
		"\u0000\u0000pn\u0001\u0000\u0000\u0000pq\u0001\u0000\u0000\u0000qr\u0001"+
		"\u0000\u0000\u0000rs\u0005\u0000\u0000\u0001s\u0001\u0001\u0000\u0000"+
		"\u0000tu\u0003\u0004\u0002\u0000uv\u0005B\u0000\u0000vx\u0001\u0000\u0000"+
		"\u0000wt\u0001\u0000\u0000\u0000xy\u0001\u0000\u0000\u0000yw\u0001\u0000"+
		"\u0000\u0000yz\u0001\u0000\u0000\u0000z\u0003\u0001\u0000\u0000\u0000"+
		"{|\u0005\u0001\u0000\u0000|}\u0003\u0006\u0003\u0000}\u0005\u0001\u0000"+
		"\u0000\u0000~\u007f\u0005I\u0000\u0000\u007f\u0007\u0001\u0000\u0000\u0000"+
		"\u0080\u0084\u0003\n\u0005\u0000\u0081\u0084\u0003\u000e\u0007\u0000\u0082"+
		"\u0084\u0003*\u0015\u0000\u0083\u0080\u0001\u0000\u0000\u0000\u0083\u0081"+
		"\u0001\u0000\u0000\u0000\u0083\u0082\u0001\u0000\u0000\u0000\u0084\t\u0001"+
		"\u0000\u0000\u0000\u0085\u0086\u0003\f\u0006\u0000\u0086\u000b\u0001\u0000"+
		"\u0000\u0000\u0087\u0088\u0005\u0004\u0000\u0000\u0088\u0089\u0005\u0003"+
		"\u0000\u0000\u0089\u008a\u0003d2\u0000\u008a\r\u0001\u0000\u0000\u0000"+
		"\u008b\u008e\u0003\u0010\b\u0000\u008c\u008e\u0003\u001c\u000e\u0000\u008d"+
		"\u008b\u0001\u0000\u0000\u0000\u008d\u008c\u0001\u0000\u0000\u0000\u008e"+
		"\u000f\u0001\u0000\u0000\u0000\u008f\u0090\u0005\n\u0000\u0000\u0090\u0091"+
		"\u0005\f\u0000\u0000\u0091\u0093\u0003\u0012\t\u0000\u0092\u0094\u0003"+
		"\u0014\n\u0000\u0093\u0092\u0001\u0000\u0000\u0000\u0094\u0095\u0001\u0000"+
		"\u0000\u0000\u0095\u0093\u0001\u0000\u0000\u0000\u0095\u0096\u0001\u0000"+
		"\u0000\u0000\u0096\u0011\u0001\u0000\u0000\u0000\u0097\u0098\u0003d2\u0000"+
		"\u0098\u0013\u0001\u0000\u0000\u0000\u0099\u009a\u0005\u0010\u0000\u0000"+
		"\u009a\u009b\u0003@ \u0000\u009b\u009d\u0003\u0016\u000b\u0000\u009c\u009e"+
		"\u0003\u001a\r\u0000\u009d\u009c\u0001\u0000\u0000\u0000\u009d\u009e\u0001"+
		"\u0000\u0000\u0000\u009e\u0015\u0001\u0000\u0000\u0000\u009f\u00a4\u0003"+
		"\u0018\f\u0000\u00a0\u00a1\u00055\u0000\u0000\u00a1\u00a3\u0003\u0018"+
		"\f\u0000\u00a2\u00a0\u0001\u0000\u0000\u0000\u00a3\u00a6\u0001\u0000\u0000"+
		"\u0000\u00a4\u00a2\u0001\u0000\u0000\u0000\u00a4\u00a5\u0001\u0000\u0000"+
		"\u0000\u00a5\u0017\u0001\u0000\u0000\u0000\u00a6\u00a4\u0001\u0000\u0000"+
		"\u0000\u00a7\u00aa\u00054\u0000\u0000\u00a8\u00aa\u0003^/\u0000\u00a9"+
		"\u00a7\u0001\u0000\u0000\u0000\u00a9\u00a8\u0001\u0000\u0000\u0000\u00aa"+
		"\u0019\u0001\u0000\u0000\u0000\u00ab\u00ac\u0005\u0011\u0000\u0000\u00ac"+
		"\u00ad\u0003R)\u0000\u00ad\u001b\u0001\u0000\u0000\u0000\u00ae\u00af\u0005"+
		"\u000b\u0000\u0000\u00af\u00b0\u0005\r\u0000\u0000\u00b0\u00b1\u0003\u0012"+
		"\t\u0000\u00b1\u00b2\u0005\u0010\u0000\u0000\u00b2\u00b3\u0003@ \u0000"+
		"\u00b3\u00b4\u00058\u0000\u0000\u00b4\u00b5\u0003\u001e\u000f\u0000\u00b5"+
		"\u00b6\u00059\u0000\u0000\u00b6\u00b7\u0005\u0014\u0000\u0000\u00b7\u00bc"+
		"\u0003$\u0012\u0000\u00b8\u00b9\u00055\u0000\u0000\u00b9\u00bb\u0003$"+
		"\u0012\u0000\u00ba\u00b8\u0001\u0000\u0000\u0000\u00bb\u00be\u0001\u0000"+
		"\u0000\u0000\u00bc\u00ba\u0001\u0000\u0000\u0000\u00bc\u00bd\u0001\u0000"+
		"\u0000\u0000\u00bd\u001d\u0001\u0000\u0000\u0000\u00be\u00bc\u0001\u0000"+
		"\u0000\u0000\u00bf\u00c4\u0003 \u0010\u0000\u00c0\u00c1\u00055\u0000\u0000"+
		"\u00c1\u00c3\u0003 \u0010\u0000\u00c2\u00c0\u0001\u0000\u0000\u0000\u00c3"+
		"\u00c6\u0001\u0000\u0000\u0000\u00c4\u00c2\u0001\u0000\u0000\u0000\u00c4"+
		"\u00c5\u0001\u0000\u0000\u0000\u00c5\u001f\u0001\u0000\u0000\u0000\u00c6"+
		"\u00c4\u0001\u0000\u0000\u0000\u00c7\u00cb\u0003f3\u0000\u00c8\u00ca\u0003"+
		"\"\u0011\u0000\u00c9\u00c8\u0001\u0000\u0000\u0000\u00ca\u00cd\u0001\u0000"+
		"\u0000\u0000\u00cb\u00c9\u0001\u0000\u0000\u0000\u00cb\u00cc\u0001\u0000"+
		"\u0000\u0000\u00cc!\u0001\u0000\u0000\u0000\u00cd\u00cb\u0001\u0000\u0000"+
		"\u0000\u00ce\u00cf\u00056\u0000\u0000\u00cf\u00d7\u0003f3\u0000\u00d0"+
		"\u00d1\u0005:\u0000\u0000\u00d1\u00d2\u0005D\u0000\u0000\u00d2\u00d7\u0005"+
		";\u0000\u0000\u00d3\u00d4\u0005:\u0000\u0000\u00d4\u00d5\u0005C\u0000"+
		"\u0000\u00d5\u00d7\u0005;\u0000\u0000\u00d6\u00ce\u0001\u0000\u0000\u0000"+
		"\u00d6\u00d0\u0001\u0000\u0000\u0000\u00d6\u00d3\u0001\u0000\u0000\u0000"+
		"\u00d7#\u0001\u0000\u0000\u0000\u00d8\u00d9\u00058\u0000\u0000\u00d9\u00da"+
		"\u0003&\u0013\u0000\u00da\u00db\u00059\u0000\u0000\u00db%\u0001\u0000"+
		"\u0000\u0000\u00dc\u00e1\u0003(\u0014\u0000\u00dd\u00de\u00055\u0000\u0000"+
		"\u00de\u00e0\u0003(\u0014\u0000\u00df\u00dd\u0001\u0000\u0000\u0000\u00e0"+
		"\u00e3\u0001\u0000\u0000\u0000\u00e1\u00df\u0001\u0000\u0000\u0000\u00e1"+
		"\u00e2\u0001\u0000\u0000\u0000\u00e2\'\u0001\u0000\u0000\u0000\u00e3\u00e1"+
		"\u0001\u0000\u0000\u0000\u00e4\u00eb\u0005C\u0000\u0000\u00e5\u00eb\u0005"+
		"D\u0000\u0000\u00e6\u00eb\u0005\u001a\u0000\u0000\u00e7\u00eb\u0005\u001b"+
		"\u0000\u0000\u00e8\u00eb\u0005\u001c\u0000\u0000\u00e9\u00eb\u0003f3\u0000"+
		"\u00ea\u00e4\u0001\u0000\u0000\u0000\u00ea\u00e5\u0001\u0000\u0000\u0000"+
		"\u00ea\u00e6\u0001\u0000\u0000\u0000\u00ea\u00e7\u0001\u0000\u0000\u0000"+
		"\u00ea\u00e8\u0001\u0000\u0000\u0000\u00ea\u00e9\u0001\u0000\u0000\u0000"+
		"\u00eb)\u0001\u0000\u0000\u0000\u00ec\u00ed\u0003,\u0016\u0000\u00ed+"+
		"\u0001\u0000\u0000\u0000\u00ee\u00f5\u0003.\u0017\u0000\u00ef\u00f5\u0003"+
		"0\u0018\u0000\u00f0\u00f5\u00032\u0019\u0000\u00f1\u00f5\u00036\u001b"+
		"\u0000\u00f2\u00f5\u00038\u001c\u0000\u00f3\u00f5\u0003B!\u0000\u00f4"+
		"\u00ee\u0001\u0000\u0000\u0000\u00f4\u00ef\u0001\u0000\u0000\u0000\u00f4"+
		"\u00f0\u0001\u0000\u0000\u0000\u00f4\u00f1\u0001\u0000\u0000\u0000\u00f4"+
		"\u00f2\u0001\u0000\u0000\u0000\u00f4\u00f3\u0001\u0000\u0000\u0000\u00f5"+
		"-\u0001\u0000\u0000\u0000\u00f6\u00f7\u0005\u0002\u0000\u0000\u00f7\u00f8"+
		"\u0005\u0003\u0000\u0000\u00f8\u00f9\u0003f3\u0000\u00f9/\u0001\u0000"+
		"\u0000\u0000\u00fa\u00fb\u0005\u0002\u0000\u0000\u00fb\u00fc\u0005\u0005"+
		"\u0000\u0000\u00fc\u00fd\u0003@ \u0000\u00fd\u00fe\u0005\u0012\u0000\u0000"+
		"\u00fe\u00ff\u0003L&\u0000\u00ff1\u0001\u0000\u0000\u0000\u0100\u0101"+
		"\u0005\u0002\u0000\u0000\u0101\u0102\u0005\u0006\u0000\u0000\u0102\u0103"+
		"\u0003@ \u0000\u0103\u0104\u00058\u0000\u0000\u0104\u0109\u00034\u001a"+
		"\u0000\u0105\u0106\u00055\u0000\u0000\u0106\u0108\u00034\u001a\u0000\u0107"+
		"\u0105\u0001\u0000\u0000\u0000\u0108\u010b\u0001\u0000\u0000\u0000\u0109"+
		"\u0107\u0001\u0000\u0000\u0000\u0109\u010a\u0001\u0000\u0000\u0000\u010a"+
		"\u010c\u0001\u0000\u0000\u0000\u010b\u0109\u0001\u0000\u0000\u0000\u010c"+
		"\u010d\u00059\u0000\u0000\u010d3\u0001\u0000\u0000\u0000\u010e\u010f\u0003"+
		"f3\u0000\u010f\u0110\u0005>\u0000\u0000\u0110\u0111\u0005D\u0000\u0000"+
		"\u01115\u0001\u0000\u0000\u0000\u0112\u0113\u0005\u0002\u0000\u0000\u0113"+
		"\u0114\u0005\u0007\u0000\u0000\u0114\u0115\u0003@ \u0000\u0115\u0116\u0005"+
		"8\u0000\u0000\u0116\u011b\u0003<\u001e\u0000\u0117\u0118\u00055\u0000"+
		"\u0000\u0118\u011a\u0003<\u001e\u0000\u0119\u0117\u0001\u0000\u0000\u0000"+
		"\u011a\u011d\u0001\u0000\u0000\u0000\u011b\u0119\u0001\u0000\u0000\u0000"+
		"\u011b\u011c\u0001\u0000\u0000\u0000\u011c\u011e\u0001\u0000\u0000\u0000"+
		"\u011d\u011b\u0001\u0000\u0000\u0000\u011e\u011f\u00059\u0000\u0000\u011f"+
		"7\u0001\u0000\u0000\u0000\u0120\u0121\u0005\u0002\u0000\u0000\u0121\u0122"+
		"\u0005\b\u0000\u0000\u0122\u0123\u0003@ \u0000\u0123\u0124\u00058\u0000"+
		"\u0000\u0124\u0129\u0003:\u001d\u0000\u0125\u0126\u00055\u0000\u0000\u0126"+
		"\u0128\u0003:\u001d\u0000\u0127\u0125\u0001\u0000\u0000\u0000\u0128\u012b"+
		"\u0001\u0000\u0000\u0000\u0129\u0127\u0001\u0000\u0000\u0000\u0129\u012a"+
		"\u0001\u0000\u0000\u0000\u012a\u012c\u0001\u0000\u0000\u0000\u012b\u0129"+
		"\u0001\u0000\u0000\u0000\u012c\u012d\u00059\u0000\u0000\u012d9\u0001\u0000"+
		"\u0000\u0000\u012e\u012f\u0003f3\u0000\u012f\u0130\u00057\u0000\u0000"+
		"\u0130\u0131\u0003J%\u0000\u0131;\u0001\u0000\u0000\u0000\u0132\u0133"+
		"\u0003f3\u0000\u0133\u0135\u0003J%\u0000\u0134\u0136\u0005\u0013\u0000"+
		"\u0000\u0135\u0134\u0001\u0000\u0000\u0000\u0135\u0136\u0001\u0000\u0000"+
		"\u0000\u0136\u0139\u0001\u0000\u0000\u0000\u0137\u0138\u0005\u0019\u0000"+
		"\u0000\u0138\u013a\u0003>\u001f\u0000\u0139\u0137\u0001\u0000\u0000\u0000"+
		"\u0139\u013a\u0001\u0000\u0000\u0000\u013a=\u0001\u0000\u0000\u0000\u013b"+
		"\u013c\u0005C\u0000\u0000\u013c?\u0001\u0000\u0000\u0000\u013d\u013e\u0003"+
		"f3\u0000\u013eA\u0001\u0000\u0000\u0000\u013f\u0140\u0005\u0002\u0000"+
		"\u0000\u0140\u0141\u0007\u0000\u0000\u0000\u0141\u0142\u0005\t\u0000\u0000"+
		"\u0142\u0143\u0003f3\u0000\u0143\u0145\u0005\u0012\u0000\u0000\u0144\u0146"+
		"\u0003D\"\u0000\u0145\u0144\u0001\u0000\u0000\u0000\u0146\u0147\u0001"+
		"\u0000\u0000\u0000\u0147\u0145\u0001\u0000\u0000\u0000\u0147\u0148\u0001"+
		"\u0000\u0000\u0000\u0148C\u0001\u0000\u0000\u0000\u0149\u014c\u0005\u0010"+
		"\u0000\u0000\u014a\u014d\u0003F#\u0000\u014b\u014d\u0003d2\u0000\u014c"+
		"\u014a\u0001\u0000\u0000\u0000\u014c\u014b\u0001\u0000\u0000\u0000\u014d"+
		"\u014e\u0001\u0000\u0000\u0000\u014e\u014f\u0005\u0012\u0000\u0000\u014f"+
		"\u0150\u0003H$\u0000\u0150E\u0001\u0000\u0000\u0000\u0151\u0152\u0005"+
		"8\u0000\u0000\u0152\u0157\u0003<\u001e\u0000\u0153\u0154\u00055\u0000"+
		"\u0000\u0154\u0156\u0003<\u001e\u0000\u0155\u0153\u0001\u0000\u0000\u0000"+
		"\u0156\u0159\u0001\u0000\u0000\u0000\u0157\u0155\u0001\u0000\u0000\u0000"+
		"\u0157\u0158\u0001\u0000\u0000\u0000\u0158\u015a\u0001\u0000\u0000\u0000"+
		"\u0159\u0157\u0001\u0000\u0000\u0000\u015a\u015b\u00059\u0000\u0000\u015b"+
		"G\u0001\u0000\u0000\u0000\u015c\u015d\u0003f3\u0000\u015dI\u0001\u0000"+
		"\u0000\u0000\u015e\u0162\u0003L&\u0000\u015f\u0162\u0003N\'\u0000\u0160"+
		"\u0162\u0003P(\u0000\u0161\u015e\u0001\u0000\u0000\u0000\u0161\u015f\u0001"+
		"\u0000\u0000\u0000\u0161\u0160\u0001\u0000\u0000\u0000\u0162K\u0001\u0000"+
		"\u0000\u0000\u0163\u018d\u0005\u001d\u0000\u0000\u0164\u018d\u0005\u001e"+
		"\u0000\u0000\u0165\u018d\u0005\u001f\u0000\u0000\u0166\u018d\u0005 \u0000"+
		"\u0000\u0167\u018d\u0005!\u0000\u0000\u0168\u018d\u0005\"\u0000\u0000"+
		"\u0169\u018d\u0005#\u0000\u0000\u016a\u018d\u0005$\u0000\u0000\u016b\u018d"+
		"\u0005%\u0000\u0000\u016c\u018d\u0005&\u0000\u0000\u016d\u018d\u0005\'"+
		"\u0000\u0000\u016e\u018d\u0005)\u0000\u0000\u016f\u0170\u0005*\u0000\u0000"+
		"\u0170\u0171\u00058\u0000\u0000\u0171\u0172\u0005D\u0000\u0000\u0172\u018d"+
		"\u00059\u0000\u0000\u0173\u018d\u0005+\u0000\u0000\u0174\u0175\u0005,"+
		"\u0000\u0000\u0175\u0176\u00058\u0000\u0000\u0176\u0177\u0005D\u0000\u0000"+
		"\u0177\u018d\u00059\u0000\u0000\u0178\u018d\u0005-\u0000\u0000\u0179\u018d"+
		"\u0005.\u0000\u0000\u017a\u017b\u0005/\u0000\u0000\u017b\u017c\u00058"+
		"\u0000\u0000\u017c\u017d\u0005D\u0000\u0000\u017d\u018d\u00059\u0000\u0000"+
		"\u017e\u017f\u00050\u0000\u0000\u017f\u0180\u00058\u0000\u0000\u0180\u0181"+
		"\u0005D\u0000\u0000\u0181\u018d\u00059\u0000\u0000\u0182\u0183\u00051"+
		"\u0000\u0000\u0183\u0184\u00058\u0000\u0000\u0184\u0185\u0005D\u0000\u0000"+
		"\u0185\u018d\u00059\u0000\u0000\u0186\u0187\u0005(\u0000\u0000\u0187\u0188"+
		"\u00058\u0000\u0000\u0188\u0189\u0005D\u0000\u0000\u0189\u018a\u00055"+
		"\u0000\u0000\u018a\u018b\u0005D\u0000\u0000\u018b\u018d\u00059\u0000\u0000"+
		"\u018c\u0163\u0001\u0000\u0000\u0000\u018c\u0164\u0001\u0000\u0000\u0000"+
		"\u018c\u0165\u0001\u0000\u0000\u0000\u018c\u0166\u0001\u0000\u0000\u0000"+
		"\u018c\u0167\u0001\u0000\u0000\u0000\u018c\u0168\u0001\u0000\u0000\u0000"+
		"\u018c\u0169\u0001\u0000\u0000\u0000\u018c\u016a\u0001\u0000\u0000\u0000"+
		"\u018c\u016b\u0001\u0000\u0000\u0000\u018c\u016c\u0001\u0000\u0000\u0000"+
		"\u018c\u016d\u0001\u0000\u0000\u0000\u018c\u016e\u0001\u0000\u0000\u0000"+
		"\u018c\u016f\u0001\u0000\u0000\u0000\u018c\u0173\u0001\u0000\u0000\u0000"+
		"\u018c\u0174\u0001\u0000\u0000\u0000\u018c\u0178\u0001\u0000\u0000\u0000"+
		"\u018c\u0179\u0001\u0000\u0000\u0000\u018c\u017a\u0001\u0000\u0000\u0000"+
		"\u018c\u017e\u0001\u0000\u0000\u0000\u018c\u0182\u0001\u0000\u0000\u0000"+
		"\u018c\u0186\u0001\u0000\u0000\u0000\u018dM\u0001\u0000\u0000\u0000\u018e"+
		"\u018f\u00052\u0000\u0000\u018f\u0190\u0005<\u0000\u0000\u0190\u0191\u0003"+
		"J%\u0000\u0191\u0192\u0005=\u0000\u0000\u0192\u019b\u0001\u0000\u0000"+
		"\u0000\u0193\u0194\u00053\u0000\u0000\u0194\u0195\u0005<\u0000\u0000\u0195"+
		"\u0196\u0003L&\u0000\u0196\u0197\u00055\u0000\u0000\u0197\u0198\u0003"+
		"J%\u0000\u0198\u0199\u0005=\u0000\u0000\u0199\u019b\u0001\u0000\u0000"+
		"\u0000\u019a\u018e\u0001\u0000\u0000\u0000\u019a\u0193\u0001\u0000\u0000"+
		"\u0000\u019bO\u0001\u0000\u0000\u0000\u019c\u019d\u0003d2\u0000\u019d"+
		"Q\u0001\u0000\u0000\u0000\u019e\u019f\u0003T*\u0000\u019fS\u0001\u0000"+
		"\u0000\u0000\u01a0\u01a5\u0003V+\u0000\u01a1\u01a2\u0005\u0015\u0000\u0000"+
		"\u01a2\u01a4\u0003V+\u0000\u01a3\u01a1\u0001\u0000\u0000\u0000\u01a4\u01a7"+
		"\u0001\u0000\u0000\u0000\u01a5\u01a3\u0001\u0000\u0000\u0000\u01a5\u01a6"+
		"\u0001\u0000\u0000\u0000\u01a6U\u0001\u0000\u0000\u0000\u01a7\u01a5\u0001"+
		"\u0000\u0000\u0000\u01a8\u01ad\u0003X,\u0000\u01a9\u01aa\u0005\u0016\u0000"+
		"\u0000\u01aa\u01ac\u0003X,\u0000\u01ab\u01a9\u0001\u0000\u0000\u0000\u01ac"+
		"\u01af\u0001\u0000\u0000\u0000\u01ad\u01ab\u0001\u0000\u0000\u0000\u01ad"+
		"\u01ae\u0001\u0000\u0000\u0000\u01aeW\u0001\u0000\u0000\u0000\u01af\u01ad"+
		"\u0001\u0000\u0000\u0000\u01b0\u01b1\u0005\u0018\u0000\u0000\u01b1\u01b4"+
		"\u0003X,\u0000\u01b2\u01b4\u0003Z-\u0000\u01b3\u01b0\u0001\u0000\u0000"+
		"\u0000\u01b3\u01b2\u0001\u0000\u0000\u0000\u01b4Y\u0001\u0000\u0000\u0000"+
		"\u01b5\u01b6\u0003\\.\u0000\u01b6\u01b7\u0003b1\u0000\u01b7\u01b8\u0003"+
		"\\.\u0000\u01b8\u01c7\u0001\u0000\u0000\u0000\u01b9\u01ba\u0003\\.\u0000"+
		"\u01ba\u01bb\u0005\u0017\u0000\u0000\u01bb\u01bc\u0005\u001c\u0000\u0000"+
		"\u01bc\u01c7\u0001\u0000\u0000\u0000\u01bd\u01be\u0003\\.\u0000\u01be"+
		"\u01bf\u0005\u0017\u0000\u0000\u01bf\u01c0\u0005\u0018\u0000\u0000\u01c0"+
		"\u01c1\u0005\u001c\u0000\u0000\u01c1\u01c7\u0001\u0000\u0000\u0000\u01c2"+
		"\u01c3\u00058\u0000\u0000\u01c3\u01c4\u0003R)\u0000\u01c4\u01c5\u0005"+
		"9\u0000\u0000\u01c5\u01c7\u0001\u0000\u0000\u0000\u01c6\u01b5\u0001\u0000"+
		"\u0000\u0000\u01c6\u01b9\u0001\u0000\u0000\u0000\u01c6\u01bd\u0001\u0000"+
		"\u0000\u0000\u01c6\u01c2\u0001\u0000\u0000\u0000\u01c7[\u0001\u0000\u0000"+
		"\u0000\u01c8\u01cb\u0003`0\u0000\u01c9\u01cb\u0003^/\u0000\u01ca\u01c8"+
		"\u0001\u0000\u0000\u0000\u01ca\u01c9\u0001\u0000\u0000\u0000\u01cb]\u0001"+
		"\u0000\u0000\u0000\u01cc\u01d1\u0003f3\u0000\u01cd\u01ce\u00056\u0000"+
		"\u0000\u01ce\u01d0\u0003f3\u0000\u01cf\u01cd\u0001\u0000\u0000\u0000\u01d0"+
		"\u01d3\u0001\u0000\u0000\u0000\u01d1\u01cf\u0001\u0000\u0000\u0000\u01d1"+
		"\u01d2\u0001\u0000\u0000\u0000\u01d2_\u0001\u0000\u0000\u0000\u01d3\u01d1"+
		"\u0001\u0000\u0000\u0000\u01d4\u01d5\u0007\u0001\u0000\u0000\u01d5a\u0001"+
		"\u0000\u0000\u0000\u01d6\u01d7\u0007\u0002\u0000\u0000\u01d7c\u0001\u0000"+
		"\u0000\u0000\u01d8\u01da\u00056\u0000\u0000\u01d9\u01d8\u0001\u0000\u0000"+
		"\u0000\u01d9\u01da\u0001\u0000\u0000\u0000\u01da\u01db\u0001\u0000\u0000"+
		"\u0000\u01db\u01e0\u0003f3\u0000\u01dc\u01dd\u00056\u0000\u0000\u01dd"+
		"\u01df\u0003f3\u0000\u01de\u01dc\u0001\u0000\u0000\u0000\u01df\u01e2\u0001"+
		"\u0000\u0000\u0000\u01e0\u01de\u0001\u0000\u0000\u0000\u01e0\u01e1\u0001"+
		"\u0000\u0000\u0000\u01e1e\u0001\u0000\u0000\u0000\u01e2\u01e0\u0001\u0000"+
		"\u0000\u0000\u01e3\u01e4\u0005E\u0000\u0000\u01e4g\u0001\u0000\u0000\u0000"+
		"#ipy\u0083\u008d\u0095\u009d\u00a4\u00a9\u00bc\u00c4\u00cb\u00d6\u00e1"+
		"\u00ea\u00f4\u0109\u011b\u0129\u0135\u0139\u0147\u014c\u0157\u0161\u018c"+
		"\u019a\u01a5\u01ad\u01b3\u01c6\u01ca\u01d1\u01d9\u01e0";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}