/* Generated By:JJTree: Do not edit this line. ASTStatementSwitch.java Version 6.1 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.serenegiant.lang.script;

public
class ASTStatementSwitch extends SimpleNode {
  public ASTStatementSwitch(int id) {
    super(id);
  }

  public ASTStatementSwitch(ScriptParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(ScriptParserVisitor visitor, Object data) {

    return
    visitor.visit(this, data);
  }
}
/* JavaCC - OriginalChecksum=b94acfe2c0c364088bff506554b03c16 (do not edit this line) */
