/* Generated By:JJTree: Do not edit this line. ASTTypedefName.java Version 6.1 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.serenegiant.lang.script;

public
class ASTTypedefName extends SimpleNode {
  public ASTTypedefName(int id) {
    super(id);
  }

  public ASTTypedefName(ScriptParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(ScriptParserVisitor visitor, Object data) {

    return
    visitor.visit(this, data);
  }
}
/* JavaCC - OriginalChecksum=438c296bb96d7e7460a5c4a7175d6493 (do not edit this line) */
