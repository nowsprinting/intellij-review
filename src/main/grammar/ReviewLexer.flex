package com.nowsprinting.intellijreview.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

import static com.nowsprinting.intellijreview.psi.ReviewTypes.*;

%%

%class _ReviewLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{
  return;
%eof}

// リスタート可能性(checkCorrectRestartOnEveryToken)を担保するため、
// レクサにユーザ定義フィールドは一切持たせない。文脈は状態名(サフィックス)で表現する。
%xstate TEXT_LINE
%xstate HEADING_LINE
%xstate HEADING_OPTION_CONTENT
%xstate HEADING_LABEL_CONTENT
%xstate BLOCK_ARGS
%xstate BLOCK_ARG_CONTENT
%xstate BLOCK_BODY
%xstate INLINE_TAG_TEXT
%xstate INLINE_BRACE_TEXT
%xstate INLINE_DOLLAR_TEXT
%xstate INLINE_PIPE_TEXT
%xstate INLINE_TAG_BODY
%xstate INLINE_BRACE_BODY
%xstate INLINE_DOLLAR_BODY
%xstate INLINE_PIPE_BODY
%xstate INLINE_TAG_HEADING
%xstate INLINE_BRACE_HEADING
%xstate INLINE_DOLLAR_HEADING
%xstate INLINE_PIPE_HEADING
%xstate INLINE_TAG_ARGS
%xstate INLINE_BRACE_ARGS
%xstate INLINE_DOLLAR_ARGS
%xstate INLINE_PIPE_ARGS

EOL=\R
HEADING_MARKER="="{1,6}
LINE_COMMENT="#@"[^\r\n]*
BLOCK_COMMAND_NAME="//"[a-z0-9]+
// Re:VIEWのリスト記法は行頭に空白が必須(空白なしの "* " は段落)。
UL_MARKER=[ \t]+\*+[ \t]
OL_MARKER=[ \t]+[0-9]+"."[ \t]
DL_MARKER=[ \t]+":"[ \t]
INLINE_NAME_CHARS=[a-zA-Z0-9]+
WS=[ \t]+

%%

// --- 行頭ディスパッチ ---------------------------------------------------
<YYINITIAL> {
  {HEADING_MARKER}      { yybegin(HEADING_LINE); return HEADING_MARKER; }
  {BLOCK_COMMAND_NAME}  { yybegin(BLOCK_ARGS); return BLOCK_COMMAND_NAME; }
  {LINE_COMMENT}        { return LINE_COMMENT; }
  {UL_MARKER}           { yybegin(TEXT_LINE); return UL_MARKER; }
  {OL_MARKER}           { yybegin(TEXT_LINE); return OL_MARKER; }
  {DL_MARKER}           { yybegin(TEXT_LINE); return DL_MARKER; }
  {EOL}                 { return EOL; }
  // どの行頭マーカーにも一致しない場合は1文字だけ押し戻してTEXT_LINEへ委譲する。
  // (TEXTを貪欲マッチさせて先頭文字だけ除外する方式だと、除外対象が増えるたびに
  // 正規表現が複雑化するため、Step 1のTEXT_LEADパターンより本方式を採用した)
  .                     { yypushback(1); yybegin(TEXT_LINE); }
}

// --- 通常テキスト行(インライン命令を含みうる) -----------------------------
<TEXT_LINE> {
  {EOL}       { yybegin(YYINITIAL); return EOL; }
  "@<"        { yybegin(INLINE_TAG_TEXT); return INLINE_START; }
  [^\r\n@]+   { return TEXT; }
  "@"         { return TEXT; }
}

// --- 見出し行 -------------------------------------------------------------
<HEADING_LINE> {
  {EOL}          { yybegin(YYINITIAL); return EOL; }
  "["            { yybegin(HEADING_OPTION_CONTENT); return LBRACKET; }
  "{"            { yybegin(HEADING_LABEL_CONTENT); return HEADING_LABEL_OPEN; }
  "@<"           { yybegin(INLINE_TAG_HEADING); return INLINE_START; }
  [^\r\n\[{@]+   { return TEXT; }
  "@"            { return TEXT; }
}

<HEADING_OPTION_CONTENT> {
  "]"          { yybegin(HEADING_LINE); return RBRACKET; }
  {EOL}        { yybegin(YYINITIAL); return EOL; }
  [^\r\n\]]+   { return HEADING_OPTION; }
}

<HEADING_LABEL_CONTENT> {
  "}"        { yybegin(HEADING_LINE); return HEADING_LABEL_CLOSE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n}]+  { return HEADING_LABEL_ID; }
}

// --- ブロック命令 -----------------------------------------------------------
<BLOCK_ARGS> {
  // 最後の "]" 直後が "{"+EOL の場合のみ複数行ブロック(本文あり)と判定する。
  "{" / {EOL}   { yybegin(BLOCK_BODY); return BLOCK_BODY_LBRACE; }
  {EOL}         { yybegin(YYINITIAL); return EOL; }
  "["           { yybegin(BLOCK_ARG_CONTENT); return LBRACKET; }
  {WS}          { return TokenType.WHITE_SPACE; }
}

<BLOCK_ARG_CONTENT> {
  "]"              { yybegin(BLOCK_ARGS); return RBRACKET; }
  "\\]"            { return BLOCK_ARG_TEXT; }
  "@<"             { yybegin(INLINE_TAG_ARGS); return INLINE_START; }
  {EOL}            { yybegin(YYINITIAL); return EOL; }
  [^\r\n\]@\\]+    { return BLOCK_ARG_TEXT; }
  "\\"             { return BLOCK_ARG_TEXT; }
  "@"              { return BLOCK_ARG_TEXT; }
}

<BLOCK_BODY> {
  {EOL}       { return EOL; }
  // "//}" はBLOCK_BODY内の行頭(BOL)でのみ終端子として扱う。
  ^"//}"      { yybegin(YYINITIAL); return BLOCK_END; }
  "@<"        { yybegin(INLINE_TAG_BODY); return INLINE_START; }
  [^\r\n@]+   { return RAW_TEXT; }
  "@"         { return RAW_TEXT; }
}

// --- インライン命令(戻り先コンテキストごとに状態を分ける。フィールドは使わない) ---

// コンテキスト: TEXT_LINE
<INLINE_TAG_TEXT> {
  {INLINE_NAME_CHARS}  { return INLINE_NAME; }
  ">"                  { return INLINE_TAG_CLOSE; }
  "{"                  { yybegin(INLINE_BRACE_TEXT); return INLINE_LBRACE; }
  "$"                  { yybegin(INLINE_DOLLAR_TEXT); return INLINE_FENCE; }
  "|"                  { yybegin(INLINE_PIPE_TEXT); return INLINE_FENCE; }
  {EOL}                { yybegin(YYINITIAL); return EOL; }
}
<INLINE_BRACE_TEXT> {
  "}"          { yybegin(TEXT_LINE); return INLINE_RBRACE; }
  "\\}"        { return INLINE_CONTENT; }
  "\\"         { return INLINE_CONTENT; }
  {EOL}        { yybegin(YYINITIAL); return EOL; }
  [^\r\n}\\]+  { return INLINE_CONTENT; }
}
<INLINE_DOLLAR_TEXT> {
  "$"        { yybegin(TEXT_LINE); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n$]+  { return INLINE_CONTENT; }
}
<INLINE_PIPE_TEXT> {
  "|"        { yybegin(TEXT_LINE); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n|]+  { return INLINE_CONTENT; }
}

// コンテキスト: BLOCK_BODY(verbatim本文)
<INLINE_TAG_BODY> {
  {INLINE_NAME_CHARS}  { return INLINE_NAME; }
  ">"                  { return INLINE_TAG_CLOSE; }
  "{"                  { yybegin(INLINE_BRACE_BODY); return INLINE_LBRACE; }
  "$"                  { yybegin(INLINE_DOLLAR_BODY); return INLINE_FENCE; }
  "|"                  { yybegin(INLINE_PIPE_BODY); return INLINE_FENCE; }
  {EOL}                { yybegin(BLOCK_BODY); return EOL; }
}
<INLINE_BRACE_BODY> {
  "}"          { yybegin(BLOCK_BODY); return INLINE_RBRACE; }
  "\\}"        { return INLINE_CONTENT; }
  "\\"         { return INLINE_CONTENT; }
  {EOL}        { yybegin(BLOCK_BODY); return EOL; }
  [^\r\n}\\]+  { return INLINE_CONTENT; }
}
<INLINE_DOLLAR_BODY> {
  "$"        { yybegin(BLOCK_BODY); return INLINE_FENCE; }
  {EOL}      { yybegin(BLOCK_BODY); return EOL; }
  [^\r\n$]+  { return INLINE_CONTENT; }
}
<INLINE_PIPE_BODY> {
  "|"        { yybegin(BLOCK_BODY); return INLINE_FENCE; }
  {EOL}      { yybegin(BLOCK_BODY); return EOL; }
  [^\r\n|]+  { return INLINE_CONTENT; }
}

// コンテキスト: HEADING_LINE(見出しキャプション)
<INLINE_TAG_HEADING> {
  {INLINE_NAME_CHARS}  { return INLINE_NAME; }
  ">"                  { return INLINE_TAG_CLOSE; }
  "{"                  { yybegin(INLINE_BRACE_HEADING); return INLINE_LBRACE; }
  "$"                  { yybegin(INLINE_DOLLAR_HEADING); return INLINE_FENCE; }
  "|"                  { yybegin(INLINE_PIPE_HEADING); return INLINE_FENCE; }
  {EOL}                { yybegin(YYINITIAL); return EOL; }
}
<INLINE_BRACE_HEADING> {
  "}"          { yybegin(HEADING_LINE); return INLINE_RBRACE; }
  "\\}"        { return INLINE_CONTENT; }
  "\\"         { return INLINE_CONTENT; }
  {EOL}        { yybegin(YYINITIAL); return EOL; }
  [^\r\n}\\]+  { return INLINE_CONTENT; }
}
<INLINE_DOLLAR_HEADING> {
  "$"        { yybegin(HEADING_LINE); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n$]+  { return INLINE_CONTENT; }
}
<INLINE_PIPE_HEADING> {
  "|"        { yybegin(HEADING_LINE); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n|]+  { return INLINE_CONTENT; }
}

// コンテキスト: BLOCK_ARG_CONTENT(ブロック命令の "[...]" 引数内)
<INLINE_TAG_ARGS> {
  {INLINE_NAME_CHARS}  { return INLINE_NAME; }
  ">"                  { return INLINE_TAG_CLOSE; }
  "{"                  { yybegin(INLINE_BRACE_ARGS); return INLINE_LBRACE; }
  "$"                  { yybegin(INLINE_DOLLAR_ARGS); return INLINE_FENCE; }
  "|"                  { yybegin(INLINE_PIPE_ARGS); return INLINE_FENCE; }
  {EOL}                { yybegin(YYINITIAL); return EOL; }
}
<INLINE_BRACE_ARGS> {
  "}"          { yybegin(BLOCK_ARG_CONTENT); return INLINE_RBRACE; }
  "\\}"        { return INLINE_CONTENT; }
  "\\"         { return INLINE_CONTENT; }
  {EOL}        { yybegin(YYINITIAL); return EOL; }
  [^\r\n}\\]+  { return INLINE_CONTENT; }
}
<INLINE_DOLLAR_ARGS> {
  "$"        { yybegin(BLOCK_ARG_CONTENT); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n$]+  { return INLINE_CONTENT; }
}
<INLINE_PIPE_ARGS> {
  "|"        { yybegin(BLOCK_ARG_CONTENT); return INLINE_FENCE; }
  {EOL}      { yybegin(YYINITIAL); return EOL; }
  [^\r\n|]+  { return INLINE_CONTENT; }
}

// --- 文字クラスに隙間がある状態のみ、明示的なフォールバックを用意する ---------
// (他の状態は正規表現の和集合が全文字を覆うため、この規則は到達不能になり
//  JFlexの dead rule 警告が出る。よって対象状態を限定している)
<BLOCK_ARGS, INLINE_TAG_TEXT, INLINE_TAG_BODY, INLINE_TAG_HEADING, INLINE_TAG_ARGS> . {
  return TokenType.BAD_CHARACTER;
}
