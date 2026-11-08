grammar GraphOrm;

// -------------------------------------------------------------
// Parser Rules
// -------------------------------------------------------------

schemaDocument
    : declaration* EOF
    ;

declaration
    : datasourceBlock
    | generatorBlock
    | modelBlock
    | viewBlock
    | compositeTypeBlock
    | enumBlock
    ;

datasourceBlock
    : DATASOURCE identifier LBRACE configEntry* RBRACE
    ;

generatorBlock
    : GENERATOR identifier LBRACE configEntry* RBRACE
    ;

configEntry
    : identifier EQUALS value
    ;

modelBlock
    : MODEL identifier LBRACE modelElement* RBRACE
    ;

modelElement
    : fieldDeclaration
    | blockAttribute
    ;

viewBlock
    : VIEW identifier LBRACE viewElement* RBRACE
    ;

viewElement
    : fieldDeclaration
    | blockAttribute
    ;

compositeTypeBlock
    : TYPE identifier LBRACE compositeTypeElement* RBRACE
    ;

compositeTypeElement
    : fieldDeclaration
    ;

enumBlock
    : ENUM identifier LBRACE enumElement* RBRACE
    ;

enumElement
    : enumValue
    | blockAttribute
    ;

enumValue
    : identifier fieldAttribute*
    ;

fieldDeclaration
    : identifier fieldType fieldAttribute*
    ;

fieldType
    : typeName optionalModifier? listModifier?
    | typeName listModifier optionalModifier?
    ;

optionalModifier
    : QUESTION
    ;

listModifier
    : LBRACKET RBRACKET
    ;

typeName
    : identifier
    ;

fieldAttribute
    : AT qualifiedIdentifier (LPAREN argumentList? RPAREN)?
    ;

blockAttribute
    : DOUBLE_AT qualifiedIdentifier (LPAREN argumentList? RPAREN)?
    ;

argumentList
    : argument (COMMA argument)* COMMA?
    ;

argument
    : identifier COLON value      # namedArgument
    | value                       # positionalArgument
    ;

value
    : STRING_LITERAL              # stringLiteralValue
    | numberLiteral               # numberLiteralValue
    | booleanLiteral              # booleanLiteralValue
    | arrayLiteral                # arrayLiteralValue
    | functionCall                # functionCallValue
    | qualifiedIdentifier         # identifierValue
    ;

numberLiteral
    : MINUS? FLOAT_LITERAL
    | MINUS? INT_LITERAL
    ;

booleanLiteral
    : TRUE
    | FALSE
    ;

arrayLiteral
    : LBRACKET (value (COMMA value)* COMMA?)? RBRACKET
    ;

functionCall
    : identifier LPAREN argumentList? RPAREN
    ;

qualifiedIdentifier
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    | DATASOURCE
    | GENERATOR
    | MODEL
    | VIEW
    | TYPE
    | ENUM
    ;

// -------------------------------------------------------------
// Lexer Rules
// -------------------------------------------------------------

DOUBLE_AT   : '@@' ;
AT          : '@' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;
COLON       : ':' ;
COMMA       : ',' ;
EQUALS      : '=' ;
QUESTION    : '?' ;
DOT         : '.' ;
MINUS       : '-' ;

DATASOURCE  : 'datasource' ;
GENERATOR   : 'generator' ;
MODEL       : 'model' ;
VIEW        : 'view' ;
TYPE        : 'type' ;
ENUM        : 'enum' ;
TRUE        : 'true' ;
FALSE       : 'false' ;

FLOAT_LITERAL : [0-9]+ '.' [0-9]+ ;
INT_LITERAL   : [0-9]+ ;

STRING_LITERAL
    : '"""' .*? '"""'
    | '\'\'\'' .*? '\'\'\''
    | '"' (~["\\\r\n] | '\\' .)* '"'
    | '\'' (~['\\\r\n] | '\\' .)* '\''
    ;

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;
