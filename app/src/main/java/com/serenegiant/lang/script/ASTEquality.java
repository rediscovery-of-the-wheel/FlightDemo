/* Generated By:JJTree: Do not edit this line. ASTEquality.java Version 6.1 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.serenegiant.lang.script;

public
class ASTEquality extends SimpleNode {
  public ASTEquality(int id) {
    super(id);
  }

  public ASTEquality(ScriptParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(ScriptParserVisitor visitor, Object data) {

    return
    visitor.visit(this, data);
  }
}
/* JavaCC - OriginalChecksum=3acf1cbef807386b7a59f0ca6dc9f842 (do not edit this line) */
